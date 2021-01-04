/*
 *  FFT.scala
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

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{OutD, OutI, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

sealed trait FFTFullUGen extends UGenSource.SingleOut {
  def in      : GE
  def size    : GE
  def padding : GE

  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD

  protected final def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, padding.expand))

  protected final def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] final def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, padding) = args
    apply(in = in.toDouble, size = size.toInt, padding = padding.toInt)
  }
}

sealed trait FFTHalfUGen extends UGenSource.SingleOut {
  def in      : GE
  def size    : GE
  def padding : GE
  def mode    : GE

  protected def apply(in: OutD, size: OutI, padding: OutI, mode: OutI)(implicit b: stream.Builder): OutD

  protected final def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, padding.expand, mode.expand))

  protected final def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] final def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, padding, mode) = args
    apply(in = in.toDouble, size = size.toInt, padding = padding.toInt, mode = mode.toInt)
  }
}

/** Forward short-time Fourier transform UGen for a real-valued input signal.
  * The FFT size is equal to `size + padding`. The output is a succession
  * of complex half-spectra, i.e. from DC to Nyquist. Depending on `mode`,
  * the output window size is either `size + padding` or `size + padding + 2`.
  *
  * @param in       the real signal to transform. If overlapping windows
  *                 are desired, a `Sliding` should already have been applied
  *                 to this signal, as well as multiplication with a window function.
  * @param size     the time domain input window size
  * @param padding  amount of zero padding for each input window.
  * @param mode     packing mode.
  *                 `0` (default) is standard "packed" mode,
  *                 whereby the real part of the bin at Nyquist is stored in the imaginary
  *                 slot of the DC. This mode allows perfect reconstruction with a
  *                 `Real1IFFT` using the same mode.
  *                 `1` is "unpacked" mode,
  *                 whereby the output windows are made two samples longer,
  *                 so that the Nyquist bin is included in the very end. By
  *                 definition, the imaginary parts of DC and Nyquist are zero.
  *                 This mode allows perfect reconstruction with a
  *                 `Real1IFFT` using the same mode.
  *                 `2` is "discarded" mode,
  *                 whereby the Nyquist bin is omitted. While it doesn't allow
  *                 a perfect reconstruction, this mode is useful for analysis,
  *                 because the output window size is equal to the fft-size,
  *                 and the imaginary part of DC is correctly zero'ed.
  */
final case class Real1FFT(in: GE, size: GE, padding: GE = 0, mode: GE = 0) extends FFTHalfUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI, mode: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1FFT(in = in, size = size, padding = padding, mode = mode)
}

/** Backward or inverse short-time Fourier transform UGen for a real-valued output signal.
  * The FFT size is equal to `size`. The output is a succession of time domain signals
  * of length `size - padding`. Depending on `mode`,
  * the output input size is supposed to be either `size` or `size + 2`.
  *
  * @param in       the complex signal to transform.
  * @param size     the frequency domain input window size (fft size)
  * @param padding  amount of zero padding for each output window. These are the number
  *                 of sample frames to drop from each output window after the FFT.
  * @param mode     packing mode of the input signal.
  *                 `0` (default) is standard "packed" mode,
  *                 whereby the real part of the bin at Nyquist is stored in the imaginary
  *                 slot of the DC. This mode allows perfect reconstruction with a
  *                 `Real1IFFT` using the same mode.
  *                 `1` is "unpacked" mode,
  *                 whereby the output windows are made two samples longer,
  *                 so that the Nyquist bin is included in the very end. By
  *                 definition, the imaginary parts of DC and Nyquist are zero.
  *                 This mode allows perfect reconstruction with a
  *                 `Real1IFFT` using the same mode.
  *                 `2` is "discarded" mode,
  *                 whereby the Nyquist bin is omitted. While it doesn't allow
  *                 a perfect reconstruction, this mode is useful for analysis,
  *                 because the output window size is equal to the fft-size,
  *                 and the imaginary part of DC is correctly zero'ed.
  */
final case class Real1IFFT(in: GE, size: GE, padding: GE = 0, mode: GE = 0) extends FFTHalfUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI, mode: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1IFFT(in = in, size = size, padding = padding, mode = mode)
}

final case class Real1FullFFT(in: GE, size: GE, padding: GE = 0) extends FFTFullUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1FullFFT(in = in, size = size, padding = padding)
}

final case class Real1FullIFFT(in: GE, size: GE, padding: GE = 0) extends FFTFullUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Real1FullIFFT(in = in, size = size, padding = padding)
}

final case class Complex1FFT(in: GE, size: GE, padding: GE = 0) extends FFTFullUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex1FFT(in = in, size = size, padding = padding)
}

final case class Complex1IFFT(in: GE, size: GE, padding: GE = 0) extends FFTFullUGen {
  protected def apply(in: OutD, size: OutI, padding: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex1IFFT(in = in, size = size, padding = padding)
}