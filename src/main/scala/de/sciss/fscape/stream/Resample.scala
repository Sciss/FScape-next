/*
 *  Resample.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape6, Inlet}
import de.sciss.fscape.stream.impl.{StageImpl, StageLogicImpl}

import scala.annotation.tailrec

object Resample {
  import math.{Pi, abs, ceil, max, min, round, sin}
  
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

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private val fltSmpPerCrossing = 4096

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape) {

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
    private[this] var fltGain     : Double        = _
    private[this] var fltBuf      : Array[Double] = _
    private[this] var fltBufD     : Array[Double] = _
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

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
      pull(sh.in3)
      pull(sh.in4)
      pull(sh.in5)
    }

    override def postStop(): Unit = {
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
        res = if (res == 0) bufMinFactor.size else min(res, bufMinFactor.size)
      }

      if (isAvailable(sh.in3)) {
        bufRollOff = grab(sh.in3)
        tryPull(sh.in3)
        res = if (res == 0) bufRollOff.size else min(res, bufRollOff.size)
      }

      if (isAvailable(sh.in4)) {
        bufKaiserBeta = grab(sh.in4)
        tryPull(sh.in4)
        res = if (res == 0) bufKaiserBeta.size else min(res, bufKaiserBeta.size)
      }

      if (isAvailable(sh.in5)) {
        bufZeroCrossings = grab(sh.in5)
        tryPull(sh.in5)
        res = if (res == 0) bufZeroCrossings.size else min(res, bufZeroCrossings.size)
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
    private[this] def shouldComplete(): Boolean = inMainRemain == 0 && isClosed(shape.in0)

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

      val flushOut = shouldComplete()
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

    private def processChunk(): Boolean = {
      var stateChange = false

      // updates all but `minFactor`
      def readAux1(): Boolean = {
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
            smpIncr = 1.0 / factor
            if (newFactor <= 1.0) {
              fltIncr = fltSmpPerCrossing * factor
              gain    = fltGain
            } else {
              fltIncr = fltSmpPerCrossing.toDouble
              gain    = fltGain * smpIncr
            }
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

      def updateTable(): Unit = {
        fltLenH = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
        fltBuf  = new Array[Double](fltLenH)
        fltBufD = new Array[Double](fltLenH - 1)
        fltGain = createAntiAliasFilter(
          fltBuf, fltBufD, halfWinSize = fltLenH, samplesPerCrossing = fltSmpPerCrossing, rollOff = rollOff,
          kaiserBeta = kaiserBeta)
      }
      
      if (init) {
        minFactor = max(0.0, bufMinFactor.buf(0))
        readAux1()
        if (minFactor == 0.0) minFactor = factor
        val minFltIncr  = fltSmpPerCrossing * min(1.0, minFactor)
        val maxFltLenH  = min(0x3FFFFFFF, round(ceil(fltLenH / minFltIncr))).toInt
        winLen          = (maxFltLenH << 1) + 1
        winBuf          = new Array[Double](winLen)
        flushRemain     = maxFltLenH
        init = false
      }

      val _winLen     = winLen
      val _maxFltLenH = _winLen >> 1
      val out         = bufOut0.buf
      val _winBuf     = winBuf

      /*
          Ex.:
         winLenH = 6; winLen = 13
         phase = 0.0
         minSrc = ((phase - winLenH).toLong + winLen) % winLen = (-6 + 13 = 7) % 13 = 7

         [ ][ ][ ][ ][ ][ ][ ][ ][ ][ ][ ][ ][ ]
                               M
          W  W  W  W  W  W  W

         framesWritten
         fwI = framesWritten % winLen
         readOff (= phase.toLong - winLenH)

       */

      val in      = bufIn.buf
      val isFlush = shouldComplete()

      var cond = true
      while (cond) {
        cond = false
        val winReadOff0 = ((inPhase.toLong - _maxFltLenH + _winLen) % _winLen).toInt
        val winReadOff1 = (winReadOff0     + _maxFltLenH          ) % _winLen
        var winWriteOff = (outPhase % _winLen).toInt
        val inRem0      = if (isFlush) flushRemain else inMainRemain
        val writeToWinLen =
          min(inRem0, (if (winReadOff0 >= winWriteOff) winReadOff0 else winReadOff0 + _winLen) - winWriteOff)

        if (writeToWinLen > 0) {
          val chunk1 = min(writeToWinLen, _winLen - winWriteOff)
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

        var readFromWinLen =
          min(outRemain, (if (winWriteOff >= winReadOff1) winWriteOff else winWriteOff + _winLen) - winReadOff1)

        if (readFromWinLen > 0) {
          while (readFromWinLen > 0) {
            if (inAuxRemain > 0) {
              val newTable = readAux1()
              inAuxOff    += 1
              inAuxRemain -= 1
              if (newTable) updateTable()
            }

            val _inPhase  = inPhase
            var _fltIncr  = fltIncr
            val _fltBuf   = fltBuf
            val _fltBufD  = fltBufD
            val _fltLenH  = fltLenH

            val q         = _inPhase % 1.0
            var value     = 0.0
            // left-hand side of window
            var srcOffI   = (_inPhase.toLong % _winLen).toInt
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
            srcOffI = ((_inPhase.toLong + 1) % _winLen).toInt
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

    // ---- aux dsp ----

    // XXX TODO --- same as in ScissDSP but with double precision; should update ScissDSP
    private def createAntiAliasFilter(impResp: Array[Double], impRespD: Array[Double],
                                      halfWinSize: Int, rollOff: Double,
                                      kaiserBeta: Double, samplesPerCrossing: Int): Double = {
      createLowPassFilter(impResp, 0.5 * rollOff, halfWinSize, kaiserBeta, samplesPerCrossing)

      if (impRespD != null) {
        var i = 0
        while (i < halfWinSize - 1) {
          impRespD(i) = impResp(i + 1) - impResp(i)
          i += 1
        }
        impRespD(i) = -impResp(i)
      }
      var dcGain = 0.0
      var j = samplesPerCrossing
      while (j < halfWinSize) {
        dcGain += impResp(j)
        j += samplesPerCrossing
      }
      dcGain = 2 * dcGain + impResp(0)

      1.0 / abs(dcGain)
    }

    // XXX TODO --- same as in ScissDSP but with double precision; should update ScissDSP
    private def createLowPassFilter(impResp: Array[Double], freq: Double, halfWinSize: Int, kaiserBeta: Double,
                                    samplesPerCrossing: Int): Unit = {
      val dNum		    = samplesPerCrossing.toDouble
      val smpRate		  = freq * 2.0

      // ideal lpf = infinite sinc-function; create truncated version
      impResp(0) = smpRate.toFloat
      var i = 1
      while (i < halfWinSize) {
        val d = Pi * i / dNum
        impResp(i) = (sin(smpRate * d) / d).toFloat
        i += 1
      }

      // apply Kaiser window
      import graph.GenWindow.Kaiser
      Kaiser.mul(winSize = halfWinSize, winOff = halfWinSize, buf = impResp, bufOff = 0, len = halfWinSize,
        param = kaiserBeta)
    }
  }
}