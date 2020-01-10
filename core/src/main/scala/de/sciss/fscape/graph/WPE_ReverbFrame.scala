/*
 *  WPE_ReverbFrame.scala
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
package graph

import de.sciss.fscape.stream.{Builder, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen implementation of a single frame Weighted Prediction Error (WPE) de-reverberation
  * algorithm in the frequency domain. It takes a DFT'ed input signal frame by frame
  * and returns the estimated reverberated components. To actually obtain the de-reverberated
  * signal, subtract the output from the input signal, then perform inverse FFT and overlap-add
  * reconstruction.
  *
  * The algorithm closely follows the Python package described in
  * L. Drude, J. Heymann, Ch. Boeddeker, R. Haeb-Umbach,
  * 'NARA-WPE: A Python package for weighted prediction error dereverberation
  * in Numpy and Tensorflow for online and offline processing' and its Numpy implementation
  * (MIT licensed).
  *
  * '''Note:''' this does not yet work correctly with multi-channel input.
  *
  * @param in         the sequence of complex FFT'ed frames. Should have been obtained through
  *                   `Real1FFT` with `mode = 1`.
  * @param psd        the power spectrum density estimation, frame by frame corresponding with `in`. It
  *                   should correspond with the shape of `in`, however being monophonic instead of
  *                   multi-channel and using real instead of complex numbers (half the signal window length).
  * @param bins       the number of frequency bins (should be `fftSize / 2 + 1`)
  * @param delay      the delay in spectral frames to avoid suppression of early reflections
  * @param taps       the filter size in spectral frames to capture the late reverberation
  * @param alpha      the decay factor for the filter coefficients
  */
final case class WPE_ReverbFrame(in: GE, psd: GE, bins: GE, delay: GE = 3, taps: GE = 10, alpha: GE = 0.9999)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UGenSource.unwrap(this, in.expand.outputs :+ psd.expand :+ bins.expand :+ delay.expand :+
      taps.expand :+ alpha.expand)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
    val numChannels = args.size - 5
    // println(s"WPE_ReverbFrame.numChannels = $numChannels")
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels)
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: Builder): Vec[StreamOut] = {
    val in :+ psd :+ bins :+ delay :+ taps :+ alpha = args
    stream.WPE_ReverbFrame(in = in.map(_.toDouble), psd = psd.toDouble, bins = bins.toInt, delay = delay.toInt,
      taps = taps.toInt, alpha = alpha.toDouble)
  }
}

/** A graph element performing end-to-end blind de-reverberation of an input signal.
  * It performs the FFT/IFFT setup around invocations of `WPE_ReverbFrame`.
  *
  * '''Note:''' this does not yet work correctly with multi-channel input.
  *
  * @param in         the reverberant time domain signal
  * @param fftSize    the fft-size
  * @param winStep    the step size for the sliding window; typically 1/4 of `fftSize`
  * @param delay      the delay in spectral frames to avoid suppression of early reflections
  * @param taps       the filter size in spectral frames to capture the late reverberation
  * @param alpha      the decay factor for the filter coefficients
  * @param psdLen     the number of preceding spectral frames to include as "context" in the psd
  */
final case class WPE_Dereverberate(in: GE, fftSize: GE = 512, winStep: GE = 128,
                                   delay: GE = 3, taps: GE = 10, alpha: GE = 0.9999,
                                   psdLen: GE = 0) extends GE {
  private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike = {
//    Sheet1D(in, 100)
    val fftSizeH = fftSize / 2
    val bins    = fftSizeH + (1: GE)
    val sl0     = Sliding(in, fftSize, winStep)
    // WTF: BufferDisk
    val sl      = BufferDisk(sl0) * GenWindow(fftSize, GenWindow.Hann).matchLen(sl0.out(0)/*.elastic(2)*/)
    // RunningSum(sl.take(fftSize).squared).last.poll("time")
    // `* fftSizeH` to match the scaling in nara_wpe
    val fft     = Real1FFT(sl, fftSize, mode = 1) * fftSizeH // .elastic()
//    Sheet1D(fft.complex.real, bins)
    // RunningSum(fft.complex.mag.take(bins).squared).last.poll("freq")
    //    Length(fft).poll("Length(fft)")
//    val psdLenC = psdLen.max(0) + 1 // XXX TODO
    val numCh   = NumChannels(in)
    val psd0    = Reduce.+(BufferDisk(fft).complex.absSquared.complex.real)
    val T       = delay + taps + 1
    val psd1    = Sliding(psd0, bins * T, bins)
    val psd2    = psd1
    //    val psd     = fft.out(0).complex.mag // psd1  // XXX TODO -- we need something like ReduceWindows or AvgWindows
//    Plot1D(psd1, bins * T, "psd1")
    val psd3    = TransposeMatrix(psd2, columns = bins, rows = T)
//    Plot1D(psd2, bins * T, "psd")
    val psd4    = ReduceWindow.+(psd3, size = T) / (T  * numCh)
    val psd = DC(0.0).take(bins * (taps + delay)) ++ psd4
//    Sheet1D(psd3, bins) // OK
//    Plot1D(psd3, bins)
//    NumChannels(psd).poll("psd.channels")
    val est     = WPE_ReverbFrame(BufferDisk(fft), bins = bins, delay = delay, taps = taps, alpha = alpha,
      psd = /*BufferMemory(psd, bins * T)*/ BufferDisk(psd))
//    Plot1D(est.drop(bins * 2 * (taps + delay + 1)).complex.real, bins)
//    val TEST = BufferDisk(fft) /*.complex - est*/ .drop(bins * 2 * (taps + delay /*+ 1*/))
//    val TEST = (BufferDisk(fft).complex - est).drop(bins * 2 * (taps + delay + 1))
//    val TEST = (est).drop(bins * 2 * (taps + delay + 1))
//    Plot1D(TEST/*.drop(bins * 2 * (taps + delay + 1))*/.complex.mag, bins)

    val OUT_VERB = true

    val gain    = winStep / (fftSize * fftSizeH) // compensation for overlap-add
    val de      = if (OUT_VERB) BufferDisk(fft).complex - est else est
    val ifft    = Real1IFFT(de, fftSize, mode = 1)
    val rec     = OverlapAdd(ifft, fftSize, winStep) * gain
    // est.complex.mag * 0.1
    rec
  }
}