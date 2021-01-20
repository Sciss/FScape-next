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

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{OutD, OutI, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

sealed trait FFT2FullUGen extends UGenSource.SingleOut {
  def in      : GE
  def rows    : GE
  def columns : GE

  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD

  protected final def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, rows.expand, columns.expand))

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
    unwrap(this, Vector(in.expand, rows.expand, columns.expand, mode.expand))

  protected final def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] final def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, rows, columns, mode) = args
    apply(in = in.toDouble, rows = rows.toInt, columns = columns.toInt, mode = mode.toInt)
  }
}

object Real2FFT extends ProductReader[Real2FFT] {
  override def read(in: RefMapIn, key: String, arity: Int): Real2FFT = {
    require (arity == 4)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    val _mode     = in.readGE()
    new Real2FFT(_in, _rows, _columns, _mode)
  }
}
/** Forward short-time Fourier transform UGen for a real-valued input signal.
  * The FFT size is equal to `rows * columns`. The output is a succession
  * of complex half-spectra, i.e. from DC to Nyquist.
  *
  * XXX TODO: Depending on `mode`,
  * the output window size is either `size + padding` or `size + padding + 2`.
  *
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
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

object Real2IFFT extends ProductReader[Real2IFFT] {
  override def read(in: RefMapIn, key: String, arity: Int): Real2IFFT = {
    require (arity == 4)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    val _mode     = in.readGE()
    new Real2IFFT(_in, _rows, _columns, _mode)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class Real2IFFT(in: GE, rows: GE, columns: GE = 0, mode: GE = 0) extends FFT2HalfUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2IFFT(in = in, rows = rows, columns = columns, mode = mode)
}

object Real2FullFFT extends ProductReader[Real2FullFFT] {
  override def read(in: RefMapIn, key: String, arity: Int): Real2FullFFT = {
    require (arity == 3)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    new Real2FullFFT(_in, _rows, _columns)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class Real2FullFFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2FullFFT(in = in, rows = rows, columns = columns)
}

object Real2FullIFFT extends ProductReader[Real2FullIFFT] {
  override def read(in: RefMapIn, key: String, arity: Int): Real2FullIFFT = {
    require (arity == 3)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    new Real2FullIFFT(_in, _rows, _columns)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class Real2FullIFFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Real2FullIFFT(in = in, rows = rows, columns = columns)
}

object Complex2FFT extends ProductReader[Complex2FFT] {
  override def read(in: RefMapIn, key: String, arity: Int): Complex2FFT = {
    require (arity == 3)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    new Complex2FFT(_in, _rows, _columns)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class Complex2FFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex2FFT(in = in, rows = rows, columns = columns)
}

object Complex2IFFT extends ProductReader[Complex2IFFT] {
  override def read(in: RefMapIn, key: String, arity: Int): Complex2IFFT = {
    require (arity == 3)
    val _in       = in.readGE()
    val _rows     = in.readGE()
    val _columns  = in.readGE()
    new Complex2IFFT(_in, _rows, _columns)
  }
}
/**
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class Complex2IFFT(in: GE, rows: GE, columns: GE = 0) extends FFT2FullUGen {
  protected def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: stream.Builder): OutD =
    stream.Complex2IFFT(in = in, rows = rows, columns = columns)
}