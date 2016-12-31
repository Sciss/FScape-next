/*
 *  FFT2.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import de.sciss.fscape.stream.impl.{Complex2FFTStageImpl, Complex2IFFTStageImpl, Real2FFTStageImpl, Real2FullFFTStageImpl, Real2FullIFFTStageImpl, Real2IFFTStageImpl}

/** Real (positive spectrum) forward Short Time Fourier Transform.
  * The counter-part of it is `Real2IFFT`.
  *
  * Useful page: http://calculator.vhex.net/calculator/fast-fourier-transform-calculator-fft/1d-discrete-fourier-transform
  */
object Real2FFT {
  def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: Builder): OutD =
    new Real2FFTStageImpl().connect(in = in, rows = rows, columns = columns, mode = mode)
}

/** Real (positive spectrum) inverse Short Time Fourier Transform.
  * The counter-part of `Real2FFT`.
  */
object Real2IFFT {
  def apply(in: OutD, rows: OutI, columns: OutI, mode: OutI)(implicit b: Builder): OutD =
    new Real2IFFTStageImpl().connect(in = in, rows = rows, columns = columns, mode = mode)
}

/** Real (full spectrum) forward Short Time Fourier Transform.
  * This produces a symmetric spectrum (redundant) spectrum, whereas
  * `Real2FFT` only produces half of the spectrum.
  * The counter-part of it is `Real2FullIFFT`.
  */
object Real2FullFFT {
  def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: Builder): OutD =
    new Real2FullFFTStageImpl().connect(in = in, rows = rows, columns = columns)
}

/** Real (full spectrum) inverse Short Time Fourier Transform.
  * This is the counter-part to `Real2FullFFT`. It assumes
  * the input is a complex spectrum of a real signal, and after the IFFT
  * drops the imaginary part.
  */
object Real2FullIFFT {
  def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: Builder): OutD =
    new Real2FullIFFTStageImpl().connect(in = in, rows = rows, columns = columns)
}

/** Complex forward Short Time Fourier Transform.
  * The counter-part of it is `Complex2IFFT`.
  */
object Complex2FFT {
  def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: Builder): OutD =
    new Complex2FFTStageImpl().connect(in = in, rows = rows, columns = columns)
}

/** Complex inverse Short Time Fourier Transform.
  * The is the counter-part to `Complex2FFT`.
  */
object Complex2IFFT {
  def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: Builder): OutD =
    new Complex2IFFTStageImpl().connect(in = in, rows = rows, columns = columns)
}