/*
 *  PitchAC.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.Ops._

object PitchAC extends ProductReader[PitchAC] {
  override def read(in: RefMapIn, key: String, arity: Int): PitchAC = {
    require (arity == 10)
    val _in                 = in.readGE()
    val _sampleRate         = in.readGE()
    val _pitchMin           = in.readGE()
    val _pitchMax           = in.readGE()
    val _numCandidates      = in.readGE()
    val _silenceThresh      = in.readGE()
    val _voicingThresh      = in.readGE()
    val _octaveCost         = in.readGE()
    val _octaveJumpCost     = in.readGE()
    val _voicedUnvoicedCost = in.readGE()
    new PitchAC(_in, _sampleRate, _pitchMin, _pitchMax, _numCandidates, _silenceThresh, _voicingThresh,
      _octaveCost, _octaveJumpCost, _voicedUnvoicedCost)
  }
}
/** A graph element that implements Boersma's auto-correlation based pitch tracking
  * algorithm.
  *
  * cf. Paul Boersma, ACCURATE SHORT-TERM ANALYSIS OF THE FUNDAMENTAL FREQUENCY AND
  * THE HARMONICS-TO-NOISE RATIO OF A SAMPLED SOUND,
  * Institute of Phonetic Sciences, University of Amsterdam, Proceedings 17 (1993), 97-110
  *
  * Note that this is currently implemented as a macro, thus being quite slow.
  * A future version might implement it directly as a UGen.
  * Currently `stepSize` is automatically given, and windowing is fixed to Hann.
  */
final case class PitchAC(in                 : GE,
                         sampleRate         : GE,
                         pitchMin           : GE = 75.0,
                         pitchMax           : GE = 600.0,
                         numCandidates      : GE = 15,
                         silenceThresh      : GE = 0.03,
                         voicingThresh      : GE = 0.45,
                         octaveCost         : GE = 0.01,
                         octaveJumpCost     : GE = 0.35,
                         voicedUnvoicedCost : GE = 0.14
                        )
  extends GE.Lazy {

  private[this] val numPeriods = 3

  val minLag  : GE = (sampleRate / pitchMax).floor  // .toInt
  val maxLag  : GE = (sampleRate / pitchMin).ceil   // .toInt
  val winSize : GE = maxLag * numPeriods
  val stepSize: GE = winSize / 4

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val numCandidatesM  = numCandidates - 1

    val winPadded       = (winSize * 1.5).ceil // .toInt
    val fftSize         = winPadded.nextPowerOfTwo
    val fftSizeH        = fftSize/2

    val inSlid          = Sliding (in = in , size = winSize, step = stepSize)
//    val numSteps: Int = ((numFrames + stepSize - 1) / stepSize).toInt

    def mkWindow()      = GenWindow(winSize, shape = GenWindow.Hann)

    val inLeak          = NormalizeWindow(inSlid, winSize, mode = NormalizeWindow.ZeroMean)
    val inW             = (inLeak * mkWindow()).matchLen(inLeak)
    val peaks0          = WindowApply(RunningMax(inLeak.abs, Metro(winSize)), winSize, winSize - 1)

    def mkAR(sig: GE): GE = {
      val fft   = Real1FFT(in = sig, size = winSize, padding = fftSize - winSize, mode = 2)
      val pow   = fft.complex.absSquared
      val ar0   = Real1IFFT(pow, size = fftSize, mode = 2)
      val ar1   = ResizeWindow(ar0, fftSize, stop = -fftSizeH)
      NormalizeWindow(ar1, size = fftSizeH, mode = NormalizeWindow.Normalize)
    }

    val r_a = mkAR(inW)
    val r_w = mkAR(mkWindow())
    val r_x = (r_a / r_w).matchLen(r_a)

    val paths = StrongestLocalMaxima(r_x, size = fftSizeH, minLag = minLag, maxLag = maxLag,
      thresh = voicingThresh * 0.5, octaveCost = octaveCost, num = numCandidatesM)

    val lags0               = paths.lags
    val strengths0          = paths.strengths
    val lags                = BufferMemory(lags0      , fftSize * 4)
    val strengths           = BufferMemory(strengths0 , fftSize * 4)
    val peaks               = BufferMemory(peaks0     , fftSize * 2)  // WTF
    val timeStepCorr        = 0.01 * sampleRate / stepSize
    val octaveJumpCostC     = octaveJumpCost      * timeStepCorr
    val voicedUnvoicedCostC = voicedUnvoicedCost  * timeStepCorr

    val vitIn = PitchesToViterbi(
      lags                = lags,
      strengths           = strengths,
      numIn               = numCandidatesM,
      peaks               = peaks,
      maxLag              = maxLag,
      voicingThresh       = voicingThresh,
      silenceThresh       = silenceThresh,
      octaveCost          = octaveCost,
      octaveJumpCost      = octaveJumpCostC,
      voicedUnvoicedCost  = voicedUnvoicedCostC,
    )

    val states    = Viterbi(add = vitIn, numStates = numCandidates)
//    val lagsB     = BufferMemory(lags, numSteps * NumCandidates)
    val lagsB     = BufferDisk(lags)  // XXX TODO --- there is no other way since we don't know the length
    val lagsSel   = WindowApply(lagsB, size = numCandidatesM, index = states, mode = 3)
    val hasFreq   = lagsSel > 0
    val freqPath  = Gate(lagsSel.reciprocal, hasFreq) * sampleRate
    freqPath
  }
}
