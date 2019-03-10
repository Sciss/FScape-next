/*
 *  Viterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape4, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, Out1IntImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

import scala.annotation.{switch, tailrec}
import scala.collection.mutable

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

  private type Shape = FanInShape4[BufD, BufD, BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.mul"       ),
      in1 = InD (s"$name.add"       ),
      in2 = InI (s"$name.numStates" ),
      in3 = InI (s"$name.numFrames" ),
      out = OutI(s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with NodeHasInitImpl with Out1IntImpl[Shape] with Out1LogicImpl[BufI, Shape] {

    private[this] var bufIn0 : BufD = _
    private[this] var bufIn1 : BufD = _
    private[this] var bufIn2 : BufI = _
    private[this] var bufIn3 : BufI = _
    protected     var bufOut0: BufI = _

    protected def out0: Outlet[BufI] = shape.out

    private[this] var inOff0: Int   = 0
    private[this] var inOff1: Int   = 0
    private[this] var inOff2: Int   = 0
    private[this] var inOff3: Int   = 0
    private[this] var outOff0: Int  = 0

    private[this] var needsNumStates: Boolean = _
    private[this] var needsNumFrames: Boolean = _

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
    
    private[this] var in0Ended  = false
    private[this] var in1Ended  = false

    private[this] var stage: Int = _

    def inValid: Boolean = throw new UnsupportedOperationException

    private final class InHandlerImpl[A](in: Inlet[A]) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        process()
      }

      setInHandler(in, this)
    }

    new InHandlerImpl(shape.in0)
    new InHandlerImpl(shape.in1)
    new InHandlerImpl(shape.in2)
    new InHandlerImpl(shape.in3)
    new ProcessOutHandlerImpl(shape.out, this)

    override protected def init(): Unit = {
      super.init()
      prepareStage0()
    }

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
      freeInputBuffers()
      freeOutputBuffers()
    }

    private def freeInputBuffers(): Unit = {
      freeBufIn0()
      freeBufIn1()
      freeBufIn2()
      freeBufIn3()
    }


    private def freeBufIn0(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    private def freeBufIn1(): Unit =
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }

    private def freeBufIn2(): Unit =
      if (bufIn2 != null) {
        bufIn2.release()
        bufIn2 = null
      }

    private def freeBufIn3(): Unit =
      if (bufIn3 != null) {
        bufIn3.release()
        bufIn3 = null
      }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    private def prepareStage0(): Unit = {
      needsNumFrames  = true
      needsNumStates  = true
      stage           = 0
    }

    // stage == 0: get new parameters
    private def processStage0(): Boolean = {
      var stateChange = false

      if (needsNumStates) {
        if (bufIn2 != null && inOff2 < bufIn2.size) {
          numStates       = math.max(1, bufIn2.buf(inOff2))
          inOff2         += 1
          needsNumStates  = false
          stateChange     = true
        } else if (isAvailable(shape.in2)) {
          freeBufIn2()
          bufIn2  = grab(shape.in2)
          inOff2  = 0
          tryPull(shape.in2)
          stateChange = true
        } else if (isClosed(shape.in2)) {
          if (numStates > 0) {
            needsNumStates  = false
            stateChange     = true
          } else {
            completeStage()
          }
        }
      }

      if (needsNumFrames) {
        if (bufIn3 != null && inOff3 < bufIn3.size) {
          val n           = bufIn3.buf(inOff3)
          numFrames       = if (n < 0) -1 else math.max(1, n)
          inOff3         += 1
          needsNumFrames  = false
          stateChange     = true
        } else if (isAvailable(shape.in3)) {
          freeBufIn3()
          bufIn3    = grab(shape.in3)
          inOff3    = 0
          tryPull(shape.in3)
          stateChange = true
        } else if (isClosed(shape.in3)) {
          if (numFrames >= -1) {
            needsNumFrames  = false
            stateChange     = true
          } else {
            completeStage()
          }
        }
      }

      if (!needsNumStates && !needsNumFrames) {
//        _auxValid   = true
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

        stateChange = true
      }

      stateChange
    }

    private def prepareStage1(): Unit = {
      if (in0Ended) {
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

      if (in1Ended) {
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

    // stage == 1: collect inner matrix
    private def processStage1(): Boolean = {
      var stateChange = false

      if (!in0Ended) {
        if (bufIn0 != null && inOff0 < bufIn0.size) {
          val chunk = math.min(statesSq - innerMulOff, bufIn0.size - inOff0)
          if (chunk > 0) {
            Util.copy(bufIn0.buf, inOff0, innerMul, innerMulOff, chunk)
            innerMulEqual = false
            inOff0       += chunk
            innerMulOff  += chunk
            stateChange   = true
          }
        } else if (isAvailable(shape.in0)) {
          freeBufIn0()
          bufIn0      = grab(shape.in0)
          inOff0      = 0
          tryPull(shape.in0)
          stateChange = true
        } else if (isClosed(shape.in0)) {
          in0Ended    = true
          stateChange = true
        }
      }

      if (!in1Ended) {
        if (bufIn1 != null && inOff1 < bufIn1.size) {
          val chunk = math.min(statesSq - innerAddOff, bufIn1.size - inOff1)
          if (chunk > 0) {
            Util.copy(bufIn1.buf, inOff1, innerAdd, innerAddOff, chunk)
            innerAddEqual = false
            inOff1       += chunk
            innerAddOff  += chunk
            stateChange   = true
          }
        } else if (isAvailable(shape.in1)) {
          freeBufIn1()
          bufIn1      = grab(shape.in1)
          inOff1      = 0
          tryPull(shape.in1)
          stateChange = true
        } else if (isClosed(shape.in1)) {
          in1Ended    = true
          stateChange = true
        }
      }

      if (innerMulOff == statesSq && innerAddOff == statesSq) {
        stage       = 2
        stateChange = true

      } else if (in0Ended && in1Ended && innerMulOff == 0 && innerAddOff == 0) {
        prepareStage3()

      } else {
        if (in0Ended) {
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
        if (in1Ended) {
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
      val framesDone = (frameOff == numFrames) || (in0Ended && in1Ended)

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

      if (bufOut0 == null) {
        bufOut0 = allocOutBuf0()
        outOff0 = 0
      }

      val chunk = math.min(writeRem, bufOut0.size - outOff0)
      if (chunk > 0) {
        val pathOff = path.length - writeRem
        Util.copy(path, pathOff, bufOut0.buf, outOff0, chunk)
        writeRem   -= chunk
        outOff0    += chunk
        stateChange = true
      }

      val stageDone = writeRem == 0
      val flush     = stageDone && in0Ended && in1Ended


      if ((outOff0 == bufOut0.size || flush) && canWrite) {
        writeOuts(outOff0)
        stateChange = true
        if (flush) completeStage()
      }

      if (stageDone && !flush) {
        prepareStage0()
      }

      stateChange
    }

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
