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
import de.sciss.fscape.Log.{stream => logStream}

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

  /** Extra size for the internal buffers; must be greater than zero.
    * The larger the `PAD` the more data can be internally buffered
    */
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
    logStream.debug(s"process() $this")
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
  private[this] var outPhase      = 0L  // the `outPhase` refers to the write-pointer from the non-resampled input

  // the `inPhase` refers to the read-pointer for producing resampled output
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

    // XXX TODO if fltLenH and fltSmpPerCrossing change, in fact should we also re-calculate the winLen
    // and create a new winBuf...? ; I don't think so, because `maxFltLenH` is basically
    // based on `fltLenH/fltSmpPerCrossing` and `fltLenH` is proportional to `fltSmpPerCrossing`.
    // The crucial element will be a change in `zeroCrossings` or `rollOff`
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
      val isFlush       = hIn.isDone
      val inRem         = if (isFlush) flushRemain else availableInFrames
      val advanceBaseW  = outPhase + _maxFltLenH
      var advance       = advanceBaseW - inPhase.toLong
      val writeToWinLen = min(inRem, _winLen - max(0, advance)).toInt

      if (writeToWinLen > 0) {
        val winWriteOff = (outPhase % _winLen).toInt
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
        advance      += writeToWinLen

        cond          = true
        stateChange   = true
      }

      var outRem        = min(auxRem, availableOutFrames)
      val advanceBaseR  = outPhase - _maxFltLenH
      advance = advanceBaseR - inPhase.toLong
      if (outRem > 0 && advance > 0) {

        var _inPhase  = inPhase
        var _inPhaseL = inPhase.toLong
        do {
          val newTable = readOneAux()
          if (newTable) updateTable()

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
          inPhaseCount += 1
          outRem       -= 1
          _inPhase      = inPhase
          _inPhaseL     = inPhase.toLong
          advance       = advanceBaseR - _inPhaseL

        } while (outRem > 0 && advance > 0)

        cond        = true
        stateChange = true
      }
    }

    stateChange
  }
}