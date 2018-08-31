/*
 *  PitchAC.scala
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
package graph

/** A graph element that implements Boersma's auto-correlation based pitch tracking
  * algorithm.
  *
  * Note that this is currently implemented as a macro, thus being quite slow.
  * A future version might implement it directly as a UGen.
  * Currently `stepSize` is automatically given, and windowing is fixed to Hann.
  */
final case class PitchAC(in: GE, sampleRate: GE,
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
    val inW             = inLeak * mkWindow()
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
    val r_x = r_a / r_w

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

    val vitIn     = PitchesToViterbi(lags = lags, strengths = strengths, numIn = numCandidatesM,
      peaks = peaks, maxLag = maxLag,
      voicingThresh = voicingThresh, silenceThresh = silenceThresh, octaveCost = octaveCost,
      octaveJumpCost = octaveJumpCostC, voicedUnvoicedCost = voicedUnvoicedCostC)

    val states    = Viterbi(add = vitIn, numStates = numCandidates)
//    val lagsB     = BufferMemory(lags, numSteps * NumCandidates)
    val lagsB     = BufferDisk(lags)  // XXX TODO --- there is no other way since we don't know the length
    val lagsSel   = WindowApply(lagsB, size = numCandidatesM, index = states, mode = 3)
    val hasFreq   = lagsSel > 0
    val freqPath  = Gate(lagsSel.reciprocal, hasFreq) * sampleRate
    freqPath
  }
}
