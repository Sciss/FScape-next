/*
 *  FFT.scala
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

package de.sciss.fscape.stream

import de.sciss.fscape.stream.impl.{Complex1FFTStageImpl, Complex1IFFTStageImpl, Real1FFTStageImpl, Real1FullFFTStageImpl, Real1FullIFFTStageImpl, Real1IFFTStageImpl}

/** Real (positive spectrum) forward Short Time Fourier Transform.
  * The counter-part of it is `Real1IFFT`.
  *
  * Useful page: http://calculator.vhex.net/calculator/fast-fourier-transform-calculator-fft/1d-discrete-fourier-transform
  */
object Real1FFT {
  def apply(in: OutD, size: OutI, padding: OutI, mode: OutI)(implicit b: Builder): OutD =
    new Real1FFTStageImpl(b.layer).connect(in = in, size = size, padding = padding, mode = mode)
}

/** Real (positive spectrum) inverse Short Time Fourier Transform.
  * The counter-part of `Real1FFT`.
  */
object Real1IFFT {
  def apply(in: OutD, size: OutI, padding: OutI, mode: OutI)(implicit b: Builder): OutD =
    new Real1IFFTStageImpl(b.layer).connect(in = in, size = size, padding = padding, mode = mode)
}

/** Real (full spectrum) forward Short Time Fourier Transform.
  * This produces a symmetric spectrum (redundant) spectrum, whereas
  * `Real1FFT` only produces half of the spectrum.
  * The counter-part of it is `Real1FullIFFT`.
  */
object Real1FullFFT {
  def apply(in: OutD, size: OutI, padding: OutI)(implicit b: Builder): OutD =
    new Real1FullFFTStageImpl(b.layer).connect(in = in, size = size, padding = padding)
}

/** Real (full spectrum) inverse Short Time Fourier Transform.
  * This is the counter-part to `Real1FullFFT`. It assumes
  * the input is a complex spectrum of a real signal, and after the IFFT
  * drops the imaginary part.
  */
object Real1FullIFFT {
  def apply(in: OutD, size: OutI, padding: OutI)(implicit b: Builder): OutD =
    new Real1FullIFFTStageImpl(b.layer).connect(in = in, size = size, padding = padding)
}

/** Complex forward Short Time Fourier Transform.
  * The counter-part of it is `Complex1IFFT`.
  */
object Complex1FFT {
  def apply(in: OutD, size: OutI, padding: OutI)(implicit b: Builder): OutD =
    new Complex1FFTStageImpl(b.layer).connect(in = in, size = size, padding = padding)
}

/** Complex inverse Short Time Fourier Transform.
  * The is the counter-part to `Complex1FFT`.
  */
object Complex1IFFT {
  def apply(in: OutD, size: OutI, padding: OutI)(implicit b: Builder): OutD =
    new Complex1IFFTStageImpl(b.layer).connect(in = in, size = size, padding = padding)
}