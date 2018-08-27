/*
 *  Viterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.impl.{NodeImpl, Out1IntImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

import scala.annotation.{switch, tailrec}
import scala.collection.mutable

object Viterbi {
  def apply(mul: OutD, add: OutD, numStates: OutI, numFrames: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(mul       , stage.in0)
    b.connect(add       , stage.in1)
    b.connect(numStates , stage.in2)
    b.connect(numFrames , stage.in3)
    stage.out
  }

  private final val name = "Viterbi"

  private type Shape = FanInShape4[BufD, BufD, BufI, BufI, BufI]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.mul"       ),
      in1 = InD (s"$name.add"       ),
      in2 = InI (s"$name.numStates" ),
      in3 = InI (s"$name.numFrames" ),
      out = OutI(s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with Out1IntImpl[Shape] with Out1LogicImpl[BufI, Shape] {

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

    private[this] var frameOff    : Int = _
    private[this] var writeRem    : Int = _
    private[this] var writeElem   : Int = _
    private[this] var innerMulOff : Int = _
    private[this] var innerAddOff : Int = _
    private[this] var innerMulClear: Boolean = _
    private[this] var innerAddClear: Boolean = _
    
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

    override def preStart(): Unit = {
      prepareStage0()
      val sh = shape
      // mul: 0, add: 1, numStates: 2, numFrames: 3
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
      pull(sh.in3)
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
          innerMulClear = true
          innerAddClear = true
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
          }
          deltaSeq  = null
          deltaSeqB = Array.newBuilder

        } else {
          // known number of frames, use two-dimensional arrays
          if (psiSeq == null || psiSeq.length != numFrames || psiSeq(0).length != numStates) {
            psiSeq = Array.ofDim(numFrames, numStates)
          }
          psiCurr = psiSeq(0)
          psiSeqB = null

          if (deltaSeq == null || deltaSeq.length != numFrames || deltaSeq(0).length != numStates) {
            deltaSeq = Array.ofDim(numFrames, numStates)
          }
          deltaCurr = deltaSeq(0)
          deltaSeqB = null
        }

        stateChange = true
      }

      stateChange
    }

    private def prepareStage1(): Unit = {
      if (in0Ended) {
        if (!innerMulClear) {
          Util.clear(innerMul, 0, statesSq)
          innerMulClear = true
        }
        innerMulOff = statesSq
      } else {
        innerMulOff = 0
      }

      if (in1Ended) {
        if (!innerAddClear) {
          Util.clear(innerAdd, 0, statesSq)
          innerAddClear = true
        }
        innerAddOff = statesSq
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
          Util.copy(bufIn0.buf, inOff0, innerMul, innerMulOff, chunk)
          innerMulClear = false
          inOff0       += chunk
          innerMulOff  += chunk
          // innerRem      = statesSq - math.min(innerMulOff, innerAddOff)
          stateChange   = true
        } else if (isAvailable(shape.in0)) {
          freeBufIn0()
          bufIn0      = grab(shape.in0)
          inOff0      = 0
          tryPull(shape.in0)
          stateChange = true
        } else if (isClosed(shape.in0)) {
          in0Ended = true
          val bothZero = innerMulOff == 0 && innerAddOff == 0
          if (bothZero) {
              if (in1Ended) prepareStage3()
          } else {
            val chunk = statesSq - innerMulOff
            Util.clear(innerMul, innerMulOff, chunk)
          }
          stateChange = true
        }
      }

      if (!in1Ended) {
        if (bufIn1 != null && inOff1 < bufIn1.size) {
          val chunk = math.min(statesSq - innerAddOff, bufIn1.size - inOff1)
          Util.copy(bufIn1.buf, inOff1, innerAdd, innerAddOff, chunk)
          innerAddClear = false
          inOff1       += chunk
          innerAddOff  += chunk
          // innerRem      = statesSq - math.min(innerMulOff, innerAddOff)
          stateChange   = true
        } else if (isAvailable(shape.in1)) {
          freeBufIn1()
          bufIn1      = grab(shape.in1)
          inOff1      = 0
          tryPull(shape.in1)
          stateChange = true
        } else if (isClosed(shape.in1)) {
          in1Ended = true
          val bothZero = innerMulOff == 0 && innerAddOff == 0
          if (bothZero) {
            if (in0Ended) prepareStage3()
          } else {
            val chunk = statesSq - innerAddOff
            Util.clear(innerAdd, innerAddOff, chunk)
          }
          stateChange = true
        }
      }

      if (innerMulOff == statesSq && innerAddOff == statesSq) {
        stage = 2
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
      var i = 0
      var k = 0
      while (i < _numStates) {
        var j = 0
        var maxVal  = Double.NegativeInfinity
        var maxIdx  = -1
        while (j < _numStates) {
          val v = _prev(j) * _mul(k) + _add(k)
          if (v > maxVal) {
            maxVal = v
            maxIdx = j
          }
          j += 1
          k += 1
        }
        _curr(i) = maxVal

        i += 1
      }

      frameOff += 1
      if ((frameOff == numFrames) || (in0Ended && in1Ended)) {
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
      writeRem = deltaSeq.length

      val _curr       = deltaSeq(writeRem - 1)
      val _numStates  = numStates
      var maxVal      = Double.NegativeInfinity
      var maxIdx      = -1
      var i = 0
      while (i < _numStates) {
        val v = _curr(i)
        if (v > maxVal) {
          maxVal = v
          maxIdx = i
        }
        i += 1
      }

      writeElem = maxIdx

      stage = 3
    }

    // stage == 3 : write
    private def processStage3(): Boolean = {
      var stateChange = false

      if (bufOut0 == null) {
        bufOut0 = allocOutBuf0()
        outOff0 = 0
      }

      var _off  = outOff0
      var _rem  = writeRem
      val chunk = math.min(_rem, bufOut0.size - _off)
      if (chunk > 0) {
        val stop  = _off + chunk
        val _buf  = bufOut0.buf
        val _psi  = psiSeq
        var _elem = writeElem
        while (_off < stop) {
          _buf(_off) = _elem
          _rem -= 1
          _elem = _psi(_rem)(_elem)
          _off += 1
        }
        writeElem = _elem
        writeRem  = _rem
        outOff0   = _off

        stateChange = true
      }

      val stageDone = _rem == 0
      val flush     = stageDone && in0Ended && in1Ended

      if ((_off == bufOut0.size || flush) && canWrite) {
        writeOuts(_off)
        stateChange = true
      }

      if (flush) {
        completeStage()
      } else if (stageDone) {
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
