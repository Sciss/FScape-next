/*
 *  ResampleImpl.scala
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
package impl

import akka.stream.{Inlet, Shape}
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}

import scala.annotation.tailrec
import scala.math._

trait ResampleImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic with Node =>

  // ---- abstract ----

  protected def in0             : InD
  protected def inFactor        : InD
  protected def inMinFactor     : InD
  protected def inRollOff       : InD
  protected def inKaiserBeta    : InD
  protected def inZeroCrossings : InI
  protected def out0            : OutD

  protected def canReadMain: Boolean
  protected def inMainValid: Boolean
  protected def readMainIns(): Int

  protected def availableInFrames : Int
  protected def availableOutFrames: Int

  protected def allocOutputBuffers(): Int

  protected def PAD: Int

  protected def processChunk(): Boolean

  protected def allocWinBuf(len: Int): Unit

  protected def clearWinBuf(off: Int, len: Int): Unit

  protected def copyInToWinBuf(winOff: Int, len: Int): Unit

  protected def clearValue(): Unit

  protected def addToValue(winOff: Int, weight: Double): Unit

  protected def copyValueToOut(): Unit

  protected def freeMainInputBuffers(): Unit

  // ---- impl ----

  protected final var bufOut0 : BufD = _

  private[this] var init          = true
  private[this] var factor        = -1.0
  private[this] var minFactor     = -1.0
  private[this] var rollOff       = -1.0
  private[this] var kaiserBeta    = -1.0
  private[this] var zeroCrossings = -1

  private[this] var bufFactor       : BufD = _
  private[this] var bufMinFactor    : BufD = _
  private[this] var bufRollOff      : BufD = _
  private[this] var bufKaiserBeta   : BufD = _
  private[this] var bufZeroCrossings: BufI = _

  protected final var inMainRemain  = 0
  protected final var inMainOff     = 0
  private[this] var inAuxRemain     = 0
  private[this] var inAuxOff        = 0

  protected final var outRemain     = 0
  protected final var outOff        = 0

  private[this] var outSent       = true

  private[this] var fltIncr     : Double        = _
  private[this] var smpIncr     : Double        = _
  protected final var gain      : Double        = _
  private[this] var flushRemain : Int           = _

  private[this] var fltLenH     : Int           = _
  private[this] var fltBuf      : Array[Double] = _
  private[this] var fltBufD     : Array[Double] = _
  private[this] var fltGain     : Double        = _
  private[this] var winLen      : Int           = _

  private[this] var _inAuxValid   = false
  private[this] var _canReadAux   = false

  // ---- handlers / constructor ----

  protected final def shouldComplete(): Boolean = inMainRemain == 0 && isClosed(in0)

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

  new AuxInHandler(inFactor       )
  new AuxInHandler(inMinFactor    )
  new AuxInHandler(inRollOff      )
  new AuxInHandler(inKaiserBeta   )
  new AuxInHandler(inZeroCrossings)

  setHandler(out0, new OutHandler {
    def onPull(): Unit = {
      logStream(s"onPull($out0)")
      updateCanWrite()
      if (canWrite) process()
    }

    override def onDownstreamFinish(): Unit = {
      logStream(s"onDownstreamFinish($out0)")
      super.onDownstreamFinish()
    }
  })

  // ---- start/stop ----

  override def preStart(): Unit = {
    pull(inFactor       )
    pull(inMinFactor    )
    pull(inRollOff      )
    pull(inKaiserBeta   )
    pull(inZeroCrossings)
  }

  override protected def stopped(): Unit = {
    fltBuf  = null
    fltBufD = null
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def freeInputBuffers(): Unit = {
    freeMainInputBuffers()
    freeAuxInputBuffers()
  }

  // ----

  @inline
  private[this] def shouldReadAux: Boolean = inAuxRemain == 0 && _canReadAux

  private def updateCanReadAux(): Unit =
    _canReadAux =
      ((isClosed(inFactor       ) && _inAuxValid) || isAvailable(inFactor       )) &&
      ((isClosed(inMinFactor    ) && _inAuxValid) || isAvailable(inMinFactor    )) &&
      ((isClosed(inRollOff      ) && _inAuxValid) || isAvailable(inRollOff      )) &&
      ((isClosed(inKaiserBeta   ) && _inAuxValid) || isAvailable(inKaiserBeta   )) &&
      ((isClosed(inZeroCrossings) && _inAuxValid) || isAvailable(inZeroCrossings))

  private def readAuxIns(): Int = {
    freeAuxInputBuffers()
    var res = 0

    if (isAvailable(inFactor)) {
      bufFactor = grab(inFactor)
      tryPull(inFactor)
      res = bufFactor.size
    }

    if (isAvailable(inMinFactor)) {
      bufMinFactor = grab(inMinFactor)
      tryPull(inMinFactor)
      res = max(res, bufMinFactor.size)
    }

    if (isAvailable(inRollOff)) {
      bufRollOff = grab(inRollOff)
      tryPull(inRollOff)
      res = max(res, bufRollOff.size)
    }

    if (isAvailable(inKaiserBeta)) {
      bufKaiserBeta = grab(inKaiserBeta)
      tryPull(inKaiserBeta)
      res = max(res, bufKaiserBeta.size)
    }

    if (isAvailable(inZeroCrossings)) {
      bufZeroCrossings = grab(inZeroCrossings)
      tryPull(inZeroCrossings)
      res = max(res, bufZeroCrossings.size)
    }

    _inAuxValid = true
    _canReadAux = false
    res
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

  final def canRead: Boolean = canReadMain && _canReadAux
  final def inValid: Boolean = inMainValid && _inAuxValid

  final def updateCanRead(): Unit     = throw new IllegalStateException("Not applicable")
  protected final def readIns(): Int  = throw new IllegalStateException("Not applicable")

  // ---- process ----

  @inline
  private[this] def shouldReadMain: Boolean = inMainRemain == 0 && canReadMain

  @tailrec
  final def process(): Unit = {
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

    if (inValid && processChunk()) stateChange = true

    val flushOut = shouldComplete() && flushRemain == 0
    if (!outSent && (outRemain == 0 || flushOut) && canWrite) {
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

  @inline
  private[this] def inPhase: Double = inPhase0 + inPhaseCount * smpIncr

  // XXX TODO --- works fine for a sine, but white-noise input overshoots
  private def updateGain(): Unit = gain = fltGain * min(1.0, factor)

  private[this] val fltSmpPerCrossing = 4096

  protected final def resample(): Boolean = {
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
      allocWinBuf(winLen)
      flushRemain     = maxFltLenH
      init = false
    }

    val _winLen     = winLen
    val _maxFltLenH = (_winLen - PAD) >> 1

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

    val isFlush = shouldComplete()

    var cond = true
    while (cond) {
      cond = false
      val winReadStop   = inPhase.toLong + _maxFltLenH
      val inRem0        = if (isFlush) flushRemain else availableInFrames
      val writeToWinLen = min(inRem0, winReadStop + PAD - outPhase).toInt

      if (writeToWinLen > 0) {
        var winWriteOff = (outPhase % _winLen).toInt
        //          println(s"writeToWinLen = $writeToWinLen; winWriteOff = $winWriteOff; _winLen = ${_winLen}")
        val chunk1      = min(writeToWinLen, _winLen - winWriteOff)
        if (chunk1 > 0) {
          if (isFlush) {
            clearWinBuf(winWriteOff, chunk1)
            flushRemain -= chunk1
          } else {
            copyInToWinBuf(winWriteOff, chunk1)
          }
        }
        val chunk2  = writeToWinLen - chunk1
        if (chunk2 > 0) {
          assert(winWriteOff + chunk1 == _winLen)
          if (isFlush) {
            clearWinBuf(0, chunk2)
            flushRemain -= chunk1
          } else {
            copyInToWinBuf(0, chunk2)
          }
        }
        outPhase     += writeToWinLen
        winWriteOff   = (winWriteOff + writeToWinLen) % _winLen

        cond          = true
        stateChange   = true
      }

      var readFromWinLen = min(availableOutFrames, outPhase - winReadStop)

      if (readFromWinLen > 0) {
        // println(s"readFromWinLen = $readFromWinLen; srcOffI = ${(inPhase.toLong % _winLen).toInt}; _winLen = ${_winLen}")
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
          clearValue()

          // left-hand side of window
          var srcOffI   = (_inPhaseL % _winLen).toInt
          var fltOff    = q * _fltIncr
          var fltOffI   = fltOff.toInt
          var srcRem    = _maxFltLenH
          while ((fltOffI < _fltLenH) && (srcRem > 0)) {
            val r    = fltOff % 1.0  // 0...1 for interpol.
            val w    = _fltBuf(fltOffI) + _fltBufD(fltOffI) * r
            addToValue(srcOffI, w)
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
            val w    = _fltBuf(fltOffI) + _fltBufD(fltOffI) * r
            addToValue(srcOffI, w)
            srcOffI += 1
            if (srcOffI == _winLen) srcOffI = 0
            srcRem  -= 1
            fltOff  += _fltIncr
            fltOffI  = fltOff.toInt
          }

          copyValueToOut()
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