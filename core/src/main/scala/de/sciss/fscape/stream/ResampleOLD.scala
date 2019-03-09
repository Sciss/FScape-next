/*
 *  ResampleOLD.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape6, Inlet}
import de.sciss.fscape.stream.impl.{StageImpl, NodeImpl}

import scala.annotation.tailrec

object ResampleOLD {
  import math.{ceil, max, min, round}
  
  def apply(in: OutD, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in           , stage.in0)
    b.connect(factor       , stage.in1)
    b.connect(minFactor    , stage.in2)
    b.connect(rollOff      , stage.in3)
    b.connect(kaiserBeta   , stage.in4)
    b.connect(zeroCrossings, stage.in5)
    stage.out
  }

  private final val name = "Resample"

  private type Shape = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"           ),
      in1 = InD (s"$name.factor"       ),
      in2 = InD (s"$name.minFactor"    ),
      in3 = InD (s"$name.rollOff"      ),
      in4 = InD (s"$name.kaiserBeta"   ),
      in5 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private val fltSmpPerCrossing = 4096

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape) {

    private[this] var init          = true
    private[this] var factor        = -1.0
    private[this] var minFactor     = -1.0
    private[this] var rollOff       = -1.0
    private[this] var kaiserBeta    = -1.0
    private[this] var zeroCrossings = -1

    private[this] var bufIn           : BufD = _
    private[this] var bufFactor       : BufD = _
    private[this] var bufMinFactor    : BufD = _
    private[this] var bufRollOff      : BufD = _
    private[this] var bufKaiserBeta   : BufD = _
    private[this] var bufZeroCrossings: BufI = _
    private[this] var bufOut0         : BufD = _

    private[this] var _inMainValid  = false
    private[this] var _inAuxValid   = false
    private[this] var _canReadMain  = false
    private[this] var _canReadAux   = false
    private[this] var _canWrite     = false

    private[this] var inMainRemain  = 0
    private[this] var inMainOff     = 0
    private[this] var inAuxRemain   = 0
    private[this] var inAuxOff      = 0

    private[this] var outSent       = true
    private[this] var outRemain     = 0
    private[this] var outOff        = 0

    private[this] var fltIncr     : Double        = _
    private[this] var smpIncr     : Double        = _
    private[this] var gain        : Double        = _
    private[this] var flushRemain : Int           = _

    private[this] var fltLenH     : Int           = _
    private[this] var fltBuf      : Array[Double] = _
    private[this] var fltBufD     : Array[Double] = _
    private[this] var fltGain     : Double        = _
    private[this] var winLen      : Int           = _
    private[this] var winBuf      : Array[Double] = _   // circular

    /*

      The idea to minimise floating point error
      is to calculate the input phase using a running
      counter of output frames for which the resampling
      factor has remained constant, like so:

      var inPhase0      = 0.0
      var inPhaseCount  = 0L
      def inPhase = inPhase0 + inPhaseCount * smpIncr

      When `factor` changes, we flush first:

      inPhase0      = inPhase
      inPhaseCount  = 0L

     */
    private[this] var inPhase0      = 0.0
    private[this] var inPhaseCount  = 0L
    private[this] var outPhase      = 0L

    // ---- handlers / constructor ----

    private class AuxInHandler[A](in: Inlet[A])
      extends InHandler {

      def onPush(): Unit = {
        logStream(s"onPush($in)")
        testRead()
      }

      private[this] def testRead(): Unit = {
        updateCanReadAux()
        if (_canReadAux) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (_inAuxValid || isAvailable(in)) {
          testRead()
        } else {
          println(s"Invalid aux $in")
          completeStage()
        }
      }

      setHandler(in, this)
    }

    new AuxInHandler(shape.in1)
    new AuxInHandler(shape.in2)
    new AuxInHandler(shape.in3)
    new AuxInHandler(shape.in4)
    new AuxInHandler(shape.in5)

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush(${shape.in0})")
        _canReadMain = true
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish(${shape.in0})")
        if (_inMainValid) process() // may lead to `flushOut`
        else {
          if (!isAvailable(shape.in0)) {
            println(s"Invalid process ${shape.in0}")
            completeStage()
          }
        }
      }
    })

    setHandler(shape.out, new OutHandler {
      def onPull(): Unit = {
        logStream(s"onPull(${shape.out})")
        _canWrite = true
        process()
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish(${shape.out})")
        super.onDownstreamFinish()
      }
    })

    // ---- start/stop ----

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf  = null
      fltBuf  = null
      fltBufD = null
      freeMainInputBuffers()
      freeAuxInputBuffers()
      freeOutputBuffers()
    }

    private def freeMainInputBuffers(): Unit =
      if (bufIn != null) {
        bufIn.release()
        bufIn = null
      }

    private def freeAuxInputBuffers(): Unit = {
      if (bufFactor != null) {
        bufFactor.release()
        bufFactor = null
      }
      if (bufMinFactor != null) {
        bufMinFactor.release()
        bufMinFactor = null
      }
      if (bufRollOff != null) {
        bufRollOff.release()
        bufRollOff = null
      }
      if (bufKaiserBeta != null) {
        bufKaiserBeta.release()
        bufKaiserBeta = null
      }
      if (bufZeroCrossings != null) {
        bufZeroCrossings.release()
        bufZeroCrossings = null
      }
    }

    private def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    private def readMainIns(): Int = {
      freeMainInputBuffers()
      bufIn = grab(shape.in0)
      tryPull(shape.in0)
      _inMainValid = true
      _canReadMain = false
      bufIn.size
    }

    private def readAuxIns(): Int = {
      freeAuxInputBuffers()
      val sh  = shape
      var res = 0

      if (isAvailable(sh.in1)) {
        bufFactor = grab(sh.in1)
        tryPull(sh.in1)
        res = bufFactor.size
      }

      if (isAvailable(sh.in2)) {
        bufMinFactor = grab(sh.in2)
        tryPull(sh.in2)
        res = max(res, bufMinFactor.size)
      }

      if (isAvailable(sh.in3)) {
        bufRollOff = grab(sh.in3)
        tryPull(sh.in3)
        res = max(res, bufRollOff.size)
      }

      if (isAvailable(sh.in4)) {
        bufKaiserBeta = grab(sh.in4)
        tryPull(sh.in4)
        res = max(res, bufKaiserBeta.size)
      }

      if (isAvailable(sh.in5)) {
        bufZeroCrossings = grab(sh.in5)
        tryPull(sh.in5)
        res = max(res, bufZeroCrossings.size)
      }

      _inAuxValid = true
      _canReadAux = false
      res
    }

    // ---- process ----

    @inline
    private[this] def shouldReadMain = inMainRemain == 0 && _canReadMain

    @inline
    private[this] def shouldReadAux  = inAuxRemain  == 0 && _canReadAux

    @inline
    private[this] def shouldComplete(): Boolean = inMainRemain == 0 && isClosed(shape.in0) && !isAvailable(shape.in0)

    private def allocOutputBuffers() = {
      bufOut0 = ctrl.borrowBufD()
      bufOut0.size
    }

    @tailrec
    private def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (shouldReadMain) {
        inMainRemain  = readMainIns()
        inMainOff     = 0
        stateChange   = true
      }
      if (shouldReadAux) {
        inAuxRemain   = readAuxIns()
        inAuxOff      = 0
        stateChange   = true
      }

      if (outSent) {
        outRemain     = allocOutputBuffers()
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      if (_inMainValid && _inAuxValid && processChunk()) stateChange = true

      val flushOut = shouldComplete() && flushRemain == 0
      if (!outSent && (outRemain == 0 || flushOut) && _canWrite) {
        writeOuts(outOff)
        outSent     = true
        stateChange = true
      }

      if (flushOut && outSent) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }

    private def writeOuts(outOff: Int): Unit = {
      if (outOff > 0) {
        bufOut0.size = outOff
        push(shape.out, bufOut0)
      } else {
        bufOut0.release()
      }
      bufOut0   = null
      _canWrite = false
    }

    private def updateCanReadAux(): Unit = {
      val sh = shape
      _canReadAux =
        ((isClosed(sh.in1) && _inAuxValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _inAuxValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _inAuxValid) || isAvailable(sh.in3)) &&
        ((isClosed(sh.in4) && _inAuxValid) || isAvailable(sh.in4)) &&
        ((isClosed(sh.in5) && _inAuxValid) || isAvailable(sh.in5))
    }

    @inline
    private[this] def inPhase: Double = inPhase0 + inPhaseCount * smpIncr

    // XXX TODO --- works fine for a sine, but white-noise input overshoots
    private def updateGain(): Unit =
      gain = fltGain * min(1.0, factor)

    // rather arbitrary, but > 1 increases speed; for matrix resample, we'd want very small to save memory
    private[this] val PAD = 32

    private def processChunk(): Boolean = {
      var stateChange = false

      // updates all but `minFactor`
      def readOneAux(): Boolean = {
        var newTable  = false
        val inAuxOffI = inAuxOff

        if (bufFactor != null && inAuxOffI < bufFactor.size) {
          val newFactor = max(0.0, bufFactor.buf(inAuxOffI))
          if (factor != newFactor) {
            if (inPhaseCount > 0) {
              inPhase0      = inPhase
              inPhaseCount  = 0L
            }
            factor  = newFactor
            smpIncr = 1.0 / newFactor
            fltIncr = fltSmpPerCrossing * min(1.0, newFactor)
            updateGain()
          }
        }

        if (bufRollOff != null && inAuxOffI < bufRollOff.size) {
          val newRollOff = max(0.0, min(1.0, bufRollOff.buf(inAuxOffI)))
          if (rollOff != newRollOff) {
            rollOff   = newRollOff
            newTable  = true
          }
        }

        if (bufKaiserBeta != null && inAuxOffI < bufKaiserBeta.size) {
          val newKaiserBeta = max(0.0, bufKaiserBeta.buf(inAuxOffI))
          if (kaiserBeta != newKaiserBeta) {
            kaiserBeta  = newKaiserBeta
            newTable    = true
          }
        }

        if (bufZeroCrossings != null && inAuxOffI < bufZeroCrossings.size) {
          val newZeroCrossings = max(1, bufZeroCrossings.buf(inAuxOffI))
          if (zeroCrossings != newZeroCrossings) {
            zeroCrossings = newZeroCrossings
            newTable      = true
          }
        }

        newTable
      }

      // XXX TODO --- since fltLenH changes, in fact we should also re-calculate the winLen
      // and create a new winBuf...
      def updateTable(): Unit = {
        fltLenH = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
        fltBuf  = new Array[Double](fltLenH)
        fltBufD = new Array[Double](fltLenH)
        fltGain = Filter.createAntiAliasFilter(
          fltBuf, fltBufD, halfWinSize = fltLenH, samplesPerCrossing = fltSmpPerCrossing, rollOff = rollOff,
          kaiserBeta = kaiserBeta)
        updateGain()
      }

      if (init) {
        minFactor = max(0.0, bufMinFactor.buf(0))
        readOneAux()
        updateTable()
        if (minFactor == 0.0) minFactor = factor
        val minFltIncr  = fltSmpPerCrossing * min(1.0, minFactor)
        val maxFltLenH  = min((0x7FFFFFFF - PAD) >> 1, round(ceil(fltLenH / minFltIncr))).toInt
        winLen          = (maxFltLenH << 1) + PAD
        winBuf          = new Array[Double](winLen)
        flushRemain     = maxFltLenH
        init = false
      }

      val _winLen     = winLen
      val _maxFltLenH = (_winLen - PAD) >> 1
      val out         = bufOut0.buf
      val _winBuf     = winBuf

      /*
        winLen = fltLen + X; X > 0

        at any one point, writeToWinLen
        is inPhaseL + fltLenH - outPhase + X

        readFromWinLen
        is outPhase - (inPhaseL + fltLenH)

        ex start:
        factor = 0.5
        fltLenH = 35; fltLen = 70; X = 1; winLen = 71
        inPhaseL = 0
        outPhase = 0

        -> writeToWinLen  = 36 -> outPhase = 36
        -> readFromWinLen =  1 -> inPhaseL =  2
        -> writeToWinLen  =  2 -> outPhase = 38
        -> readFromWinLen =  1 -> inPhaseL =  4
        -> eventually: no write
        -> readFromWinLen =  0 -> exit loop

       */

      val in      = bufIn.buf
      val isFlush = shouldComplete()

      var cond = true
      while (cond) {
        cond = false
        val winReadStop   = inPhase.toLong + _maxFltLenH
        val inRem0        = if (isFlush) flushRemain else inMainRemain
        val writeToWinLen = min(inRem0, winReadStop + PAD - outPhase).toInt

        if (writeToWinLen > 0) {
          var winWriteOff = (outPhase % _winLen).toInt
//          println(s"writeToWinLen = $writeToWinLen; winWriteOff = $winWriteOff; _winLen = ${_winLen}")
          val chunk1      = min(writeToWinLen, _winLen - winWriteOff)
          if (chunk1 > 0) {
            if (isFlush) {
              Util.clear(_winBuf, winWriteOff, chunk1)
              flushRemain  -= chunk1
            } else {
              Util.copy(in, inMainOff, _winBuf, winWriteOff, chunk1)
              inMainOff    += chunk1
              inMainRemain -= chunk1
            }
          }
          val chunk2  = writeToWinLen - chunk1
          if (chunk2 > 0) {
            assert(winWriteOff + chunk1 == _winLen)
            if (isFlush) {
              Util.clear(_winBuf, 0, chunk2)
              flushRemain  -= chunk1
            } else {
              Util.copy(in, inMainOff, _winBuf, 0, chunk2)
              inMainOff    += chunk2
              inMainRemain -= chunk2
            }
          }
          outPhase     += writeToWinLen
          winWriteOff   = (winWriteOff + writeToWinLen) % _winLen

          cond          = true
          stateChange   = true
        }

        var readFromWinLen = min(outRemain, outPhase - winReadStop)

        if (readFromWinLen > 0) {
//          println(s"readFromWinLen = $readFromWinLen; srcOffI = ${(inPhase.toLong % _winLen).toInt}; _winLen = ${_winLen}")
          while (readFromWinLen > 0) {
            if (inAuxRemain > 0) {
              val newTable = readOneAux()
              inAuxOff    += 1
              inAuxRemain -= 1
              if (newTable) updateTable()
            }

            val _inPhase  = inPhase
            val _inPhaseL = inPhase.toLong
            val _fltIncr  = fltIncr
            val _fltBuf   = fltBuf
            val _fltBufD  = fltBufD
            val _fltLenH  = fltLenH

            val q         = _inPhase % 1.0
            var value     = 0.0
            // left-hand side of window
            var srcOffI   = (_inPhaseL % _winLen).toInt
            var fltOff    = q * _fltIncr
            var fltOffI   = fltOff.toInt
            var srcRem    = _maxFltLenH
            while ((fltOffI < _fltLenH) && (srcRem > 0)) {
              val r    = fltOff % 1.0  // 0...1 for interpol.
              value   += _winBuf(srcOffI) * (_fltBuf(fltOffI) + _fltBufD(fltOffI) * r)
              srcOffI -= 1
              if (srcOffI < 0) srcOffI += _winLen
              srcRem  -= 1
              fltOff  += _fltIncr
              fltOffI  = fltOff.toInt
            }

            // right-hand side of window
            srcOffI = ((_inPhaseL + 1) % _winLen).toInt
            fltOff  = (1.0 - q) * _fltIncr
            fltOffI = fltOff.toInt
            srcRem  = _maxFltLenH - 1
            while ((fltOffI < _fltLenH) && (srcRem > 0)) {
              val r    = fltOff % 1.0  // 0...1 for interpol.
              value   += _winBuf(srcOffI) * (_fltBuf(fltOffI) + _fltBufD(fltOffI) * r)
              srcOffI += 1
              if (srcOffI == _winLen) srcOffI = 0
              srcRem  -= 1
              fltOff  += _fltIncr
              fltOffI  = fltOff.toInt
            }

            out(outOff) = value * gain
            outOff         += 1
            outRemain      -= 1
            inPhaseCount   += 1
            readFromWinLen -= 1
          }
          cond        = true
          stateChange = true
        }
      }

      stateChange
    }
  }
}