/*
 *  ResampleImpl.scala
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
package impl

import akka.stream.{Inlet, Shape}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}

import scala.annotation.tailrec
import scala.math._

abstract class ResampleImpl[S <: Shape](name: String, layer: Layer, shape: S)(implicit control: Control)
  extends Handlers(name, layer, shape) {

  // ---- abstract ----

  protected def hIn             : InDMain
  protected def hFactor         : InDAux  // max(0.0, _)
  protected def hMinFactor      : InDAux  // max(0.0, _)
  protected def hRollOff        : InDAux  // _.clip(0.0, 1.0)
  protected def hKaiserBeta     : InDAux  // max(1.0, _)
  protected def hZeroCrossings  : InIAux  // max(1, _)
  protected def hOut            : OutDMain

  protected def availableInFrames : Int
  protected def availableOutFrames: Int

  // NOTE: there seems to be a bug when using higher values than around 8
  protected def PAD: Int

  protected def processChunk(): Boolean

  protected def allocWinBuf(len: Int): Unit

  protected def clearWinBuf(off: Int, len: Int): Unit

  protected def copyInToWinBuf(winOff: Int, len: Int): Unit

  protected def clearValue(): Unit

  protected def addToValue(winOff: Int, weight: Double): Unit

  protected def copyValueToOut(): Unit

  // ---- impl ----

  private[this] var init          = true
  private[this] var factor        = -1.0
  private[this] var minFactor     = -1.0
  private[this] var rollOff       = -1.0
  private[this] var kaiserBeta    = -1.0
  private[this] var zeroCrossings = -1

  private[this] var fltIncr     : Double        = _
  private[this] var smpIncr     : Double        = _
  protected final var gain      : Double        = _
  private[this] var flushRemain : Int           = _

  private[this] var fltLenH     : Int           = _
  private[this] var fltBuf      : Array[Double] = _
  private[this] var fltBufD     : Array[Double] = _
  private[this] var fltGain     : Double        = _
  private[this] var winLen      : Int           = _

  // ---- start/stop ----

  override protected def stopped(): Unit = {
    super.stopped()
    fltBuf  = null
    fltBufD = null
  }

  // ---- process ----

  protected def onDone(inlet: Inlet[_]): Unit =
    process()

  @tailrec
  final def process(): Unit = {
    logStream(s"process() $this")
    val stateChange = processChunk()

    val flushOut = hIn.isDone && flushRemain == 0
    if (flushOut && hOut.flush()) {
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

    var auxRem =
      min(hFactor.available, min(hRollOff.available, min(hKaiserBeta.available, hZeroCrossings.available)))

    // updates all but `minFactor`
    def readOneAux(): Boolean = {
      var newTable  = false

      val newFactor = hFactor.next()
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

      val newRollOff = hRollOff.next()
      if (rollOff != newRollOff) {
        rollOff   = newRollOff
        newTable  = true
      }

      val newKaiserBeta = hKaiserBeta.next()
      if (kaiserBeta != newKaiserBeta) {
        kaiserBeta  = newKaiserBeta
        newTable    = true
      }

      val newZeroCrossings = hZeroCrossings.next()
      if (zeroCrossings != newZeroCrossings) {
        zeroCrossings = newZeroCrossings
        newTable      = true
      }

      auxRem -= 1
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

    if (auxRem == 0) return false

    if (init) {
      if (!hMinFactor.hasNext) return false
      minFactor = hMinFactor.next()
      readOneAux()
      updateTable()
      if (minFactor == 0.0) minFactor = factor
      val minFltIncr  = fltSmpPerCrossing * min(1.0, minFactor)
      val maxFltLenH  = min((0x7FFFFFFF - PAD) >> 1, round(ceil(fltLenH / minFltIncr))).toInt
      winLen          = (maxFltLenH << 1) + PAD
      allocWinBuf(winLen)
      flushRemain     = maxFltLenH
      init            = false
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

    var cond = true
    while (cond) {
      cond = false
      val winReadStop   = inPhase.toLong + _maxFltLenH
      val isFlush       = hIn.isDone
      val inRem0        = if (isFlush) flushRemain else availableInFrames
      val writeToWinLen = min(inRem0, winReadStop + PAD - outPhase).toInt

      // XXX TODO --- we have to investigate this additional constraint;
      // we need it because otherwise, we might write more than a full winBuf,
      // and chunk2 might become too large and cause an ArrayIndexOutOfBoundsException
//      val writeToWinLen = min(min(inRem0, winReadStop + PAD - outPhase).toInt, _winLen)

      if (writeToWinLen > 0) {
        val winWriteOff = (outPhase % _winLen).toInt
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
            flushRemain -= chunk2
          } else {
            copyInToWinBuf(0, chunk2)
          }
        }
        outPhase     += writeToWinLen
        // winWriteOff   = (winWriteOff + writeToWinLen) % _winLen // UNUSED

        cond          = true
        stateChange   = true
      }

      var readFromWinLen = min(auxRem, min(availableOutFrames, outPhase - winReadStop))

      if (readFromWinLen > 0) {
        // println(s"readFromWinLen = $readFromWinLen; srcOffI = ${(inPhase.toLong % _winLen).toInt}; _winLen = ${_winLen}")
        while (readFromWinLen > 0) {
          val newTable = readOneAux()
          if (newTable) updateTable()

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