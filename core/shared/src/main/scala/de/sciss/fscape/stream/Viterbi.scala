/*
 *  Viterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape4, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.{switch, tailrec}
import scala.collection.mutable
import scala.math.{max, min}

object Viterbi {
  def apply(mul: OutD, add: OutD, numStates: OutI, numFrames: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(mul       , stage.in0)
    b.connect(add       , stage.in1)
    b.connect(numStates , stage.in2)
    b.connect(numFrames , stage.in3)
    stage.out
  }

  private final val name = "Viterbi"

  private type Shp = FanInShape4[BufD, BufD, BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.mul"       ),
      in1 = InD (s"$name.add"       ),
      in2 = InI (s"$name.numStates" ),
      in3 = InI (s"$name.numFrames" ),
      out = OutI(s"$name.out"       )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hInMul      = Handlers.InDMain  (this, shape.in0)
    private[this] val hInAdd      = Handlers.InDMain  (this, shape.in1)
    private[this] val hNumStates  = Handlers.InIAux   (this, shape.in2)(max(1, _))
    private[this] val hNumFrames  = Handlers.InIAux   (this, shape.in3)(n => if (n < 0) -1 else max(1, n))
    private[this] val hOut        = Handlers.OutIMain (this, shape.out)

    private[this] var needsNum: Boolean = true

    private[this] var numStates = 0
    private[this] var statesSq  = 0
    private[this] var numFrames = -2

    private[this] var innerMul  : Array[Double] = _
    private[this] var innerAdd  : Array[Double] = _

    private[this] var deltaPrev : Array[Double] = _
    private[this] var deltaCurr : Array[Double] = _
    private[this] var psiCurr   : Array[Int]    = _

    private[this] var deltaSeq  : Array[Array[Double]] = _
    private[this] var psiSeq    : Array[Array[Int   ]] = _
    private[this] var deltaSeqB : mutable.Builder[Array[Double], Array[Array[Double]]] = _
    private[this] var psiSeqB   : mutable.Builder[Array[Int   ], Array[Array[Int   ]]] = _
    private[this] var path      : Array[Int] = _

    private[this] var frameOff    : Int = _
    private[this] var writeRem    : Int = _
    private[this] var innerMulOff : Int = _
    private[this] var innerAddOff : Int = _
    private[this] var innerMulEqual: Boolean = _
    private[this] var innerAddEqual: Boolean = _

    private[this] var stage: Int = 0

    override protected def stopped(): Unit = {
      super.stopped()
      innerMul  = null
      innerAdd  = null
      deltaPrev = null
      deltaCurr = null
      deltaSeq  = null
      deltaSeqB = null
      psiCurr   = null
      psiSeq    = null
      psiSeqB   = null
    }

    private def prepareStage0(): Unit = {
      needsNum  = true
      stage     = 0
    }

    // stage == 0: get new parameters
    private def processStage0(): Boolean = {
      if (needsNum) {
        if (!(hNumStates.hasNext && hNumFrames.hasNext)) return false

        numStates = hNumStates.next()
        numFrames = hNumFrames.next()
        needsNum  = false
      }

      val _statesSq = numStates * numStates
      if (statesSq != _statesSq) {
        statesSq      = _statesSq
        innerMul      = new Array(_statesSq)
        innerAdd      = new Array(_statesSq)
        innerMulEqual = true
        innerAddEqual = true
      }

      prepareStage1()

      frameOff = 0
      if (numFrames < 0) {
        // unknown number of frames, use array builders
        if (psiCurr == null || psiCurr.length != numStates) {
          psiCurr = new Array(numStates)
        }
        psiSeq  = null
        psiSeqB = Array.newBuilder

        if (deltaCurr == null || deltaCurr.length != numStates) {
          deltaCurr = new Array(numStates)
          deltaPrev = new Array(numStates)
        }
        deltaSeq  = null
        deltaSeqB = Array.newBuilder

      } else {
        val numFramesP = numFrames + 1  // we ensure >= 2 and valid access to `numFrames`
        // known number of frames, use two-dimensional arrays
        if (psiSeq == null || psiSeq.length != numFramesP || psiSeq(0).length != numStates) {
          psiSeq = Array.ofDim(numFramesP, numStates)
        }
        psiCurr = psiSeq(0)
        psiSeqB = null

        if (deltaSeq == null || deltaSeq.length != numFramesP || deltaSeq(0).length != numStates) {
          deltaSeq = Array.ofDim(numFramesP, numStates)
        }
        deltaCurr = deltaSeq(0)
        deltaPrev = deltaSeq(numFramesP - 1) // doesn't matter as long as it's not identical to deltaCurr
        deltaSeqB = null
      }

      true
    }

    private def prepareStage1(): Unit = {
      if (hInMul.isDone) {
        val _statesSq = statesSq
        if (!innerMulEqual) {
          val _buf      = innerMul
          val mul       = _buf(_statesSq - 1)
          Util.fill(_buf, 0, _statesSq, mul)
          innerMulEqual = true
        }
        innerMulOff = _statesSq
      } else {
        innerMulOff = 0
      }

      if (hInAdd.isDone) {
        val _statesSq = statesSq
        if (!innerAddEqual) {
          val _buf      = innerAdd
          val add       = _buf(_statesSq - 1)
          Util.fill(_buf, 0, _statesSq, add)
          innerAddEqual = true
        }
        innerAddOff = _statesSq
      } else {
        innerAddOff = 0
      }

      stage = 1
    }

    private def insEnded: Boolean = hInMul.isDone && hInAdd.isDone

    // stage == 1: collect inner matrix
    private def processStage1(): Boolean = {
      var stateChange = false

      {
        val chunk = min(statesSq - innerMulOff, hInMul.available)
        if (chunk > 0) {
          hInMul.nextN(innerMul, innerMulOff, chunk)
          innerMulEqual = false
          innerMulOff  += chunk
          stateChange   = true
        }
      }

      {
        val chunk = min(statesSq - innerAddOff, hInAdd.available)
        if (chunk > 0) {
          hInAdd.nextN(innerAdd, innerAddOff, chunk)
          innerAddEqual = false
          innerAddOff  += chunk
          stateChange   = true
        }
      }

      if (innerMulOff == statesSq && innerAddOff == statesSq) {
        stage       = 2
        stateChange = true

      } else if (insEnded && innerMulOff == 0 && innerAddOff == 0) {
        prepareStage3()

      } else {
        if (hInMul.isDone) {
          val _mulOff   = innerMulOff
          val _statesSq = statesSq
          val chunk     = _statesSq - _mulOff
          if (chunk > 0) {
            val _buf      = innerMul
            innerMulEqual = _mulOff == 0
            val mul       = if (innerMulEqual) _buf(_statesSq - 1) else _buf(_mulOff - 1)
            Util.fill(_buf, _mulOff, chunk, mul)
            innerMulOff   = _statesSq
            stateChange   = true
          }
        }
        if (hInAdd.isDone) {
          val _addOff   = innerAddOff
          val _statesSq = statesSq
          val chunk     = _statesSq - _addOff
          if (chunk > 0) {
            val _buf      = innerAdd
            innerAddEqual = _addOff == 0
            val add       = if (innerAddEqual) _buf(_statesSq - 1) else _buf(_addOff - 1)
            Util.fill(_buf, _addOff, chunk, add)
            innerAddOff   = _statesSq
            stateChange   = true
          }
        }
      }

      stateChange
    }

    // stage == 2: advance delta one frame
    private def processStage2(): Boolean = {
      val _numStates  = numStates
      val _mul        = innerMul
      val _add        = innerAdd
      val _prev       = deltaPrev
      val _curr       = deltaCurr
      val _psi        = psiCurr
      var i = 0
      var k = 0
      while (i < _numStates) {
        var j = 0
        var maxVal  = Double.NegativeInfinity
        var maxIdx  = -1
        while (j < _numStates) {
//          if (_prev == null || _mul == null || _add == null) {
//            println("AQUI")
//          }
          val v = _prev(j) * _mul(k) + _add(k)
          if (v > maxVal) {
            maxVal = v
            maxIdx = j
          }
          j += 1
          k += 1
        }
        _curr(i) = maxVal
        _psi (i) = maxIdx

        i += 1
      }

      deltaPrev  = _curr
      frameOff  += 1
      val framesDone = (frameOff == numFrames) || insEnded

      if (deltaSeqB == null) {
        deltaCurr = deltaSeq(frameOff)
        psiCurr   = psiSeq  (frameOff)
      } else if (!framesDone) {
        deltaSeqB += _curr
        psiSeqB   += _psi
        deltaCurr = new Array(numStates)
        psiCurr   = new Array(numStates)
      }

      if (framesDone) {
        prepareStage3()
      } else {
        prepareStage1()
      }
      true
    }

    private def prepareStage3(): Unit = {
      if (deltaSeqB != null) {
        deltaSeq  = deltaSeqB.result()
        deltaSeqB = null
      }
      if (psiSeqB != null) {
        psiSeq  = psiSeqB.result()
        psiSeqB = null
      }
      val _deltaSeq = deltaSeq
      val _psi      = psiSeq
      var rem       = _deltaSeq.length
      writeRem      = rem
      assert(_psi.length == rem)

      // because the backtracking will produce the
      // time reversed sequence, we perform it here
      // completely and simply write the results
      // into the first indices of psi

      val _path = new Array[Int](rem)

      rem -= 1
      val _curr       = if (rem >= 0) _deltaSeq(rem) else null
      val _numStates  = numStates
      var maxVal      = Double.NegativeInfinity
      var pathIdx     = -1
      var i = 0
      while (i < _numStates) {
        val v = _curr(i)
        if (v > maxVal) {
          maxVal = v
          pathIdx = i
        }
        i += 1
      }
      // now pathIdx is the index of the maximum
      // element in the last frame

      while (rem >= 0) {
        _path(rem) = pathIdx
        pathIdx    = _psi(rem)(pathIdx)
        rem       -= 1
      }

      path  = _path
      stage = 3
    }

    // stage == 3 : write
    private def processStage3(): Boolean = {
      var stateChange = false

      val chunk = min(writeRem, hOut.available)
      if (chunk > 0) {
        val pathOff = path.length - writeRem
        hOut.nextN(path, pathOff, chunk)
        writeRem   -= chunk
        stateChange = true
      }

      val stageDone = writeRem == 0
      val flush     = stageDone && insEnded

      if (flush && hOut.flush()) {
        completeStage()
        return false
      }

      if (stageDone && !flush) {
        prepareStage0()
      }

      stateChange
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    @tailrec
    def process(): Unit = {
      val stateChange = (stage: @switch) match {
        case 0 => processStage0()
        case 1 => processStage1()
        case 2 => processStage2()
        case 3 => processStage3()
      }

      if (stateChange) process()
    }
  }
}
