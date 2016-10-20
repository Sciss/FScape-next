/*
 *  FFT.scala
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
package graph

import de.sciss.fscape.stream.{OutD, OutI, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

sealed trait FFT2FullUGen extends UGenSource.SingleOut {
  def in      : GE
  def rows    : GE
  def columns : GE

  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD

  protected final def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, rows.expand, columns.expand))

  protected final def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] final def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns) = args
    apply(in = in.toDouble, rows = rows.toInt, columns = columns.toInt)
  }
}

sealed trait FFT2HalfUGen extends UGenSource.SingleOut {
  def in      : GE
  def rows    : GE
  def columns : GE
  def mode    : GE

  protected def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: stream.Builder): OutD

  protected final def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, rows.expand, columns.expand, mode.expand))

  protected final def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] final def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns, mode) = args
    apply(in = in.toDouble, rows = rows.toInt, columns = columns.toInt, mode = mode.toInt)
  }
}

/** Forward short-time Fourier transform UGen for a real-valued input signal.
  * The FFT size is equal to `rows * columns`. The output is a succession
  * of complex half-spectra, i.e. from DC to Nyquist.
  *
  * XXX TODO: Depending on `mode`,
  * the output window size is either `size + padding` or `size + padding + 2`.
  *
  * @param in       the real signal to transform. If overlapping windows
  *                 are desired, a `Sliding` should already have been applied
  *                 to this signal, as well as multiplication with a window function.
  * @param rows     the input matrix number of rows
  * @param columns  the input matrix number of columns
  * @param mode     packing mode.
  *                 `0` (default) is standard "packed" mode,
  *                 whereby the real part of the bin at Nyquist is stored in the imaginary
  *                 slot of the DC. This mode allows perfect reconstruction with a
  *                 `Real2IFFT` using the same mode.
  *                 `1` is "unpacked" mode,
  *                 whereby the output windows are made two samples longer,
  *                 so that the Nyquist bin is included in the very end. By
  *                 definition, the imaginary parts of DC and Nyquist are zero.
  *                 This mode allows perfect reconstruction with a
  *                 `Real2IFFT` using the same mode.
  *                 `2` is "discarded" mode,
  *                 whereby the Nyquist bin is omitted. While it doesn't allow
  *                 a perfect reconstruction, this mode is useful for analysis,
  *                 because the output window size is equal to the fft-size,
  *                 and the imaginary part of DC is correctly zero'ed.
  */
final case class Real2FFT(in: GE, rows: GE, columns: GE = 0, mode: GE = 0) extends FFT2HalfUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2FFT(in = in, rows = rows, columns = columns, mode = mode)
}

final case class Real2IFFT(in: GE, rows: GE, columns: GE = 0, mode: GE = 0) extends FFT2HalfUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2IFFT(in = in, rows = rows, columns = columns, mode = mode)
}

final case class Real2FullFFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2FullFFT(in = in, rows = rows, columns = columns)
}

final case class Real2FullIFFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2FullIFFT(in = in, rows = rows, columns = columns)
}

final case class Complex2FFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex2FFT(in = in, rows = rows, columns = columns)
}

final case class Complex2IFFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex2IFFT(in = in, rows = rows, columns = columns)
}