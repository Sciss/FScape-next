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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.GraphDSL
import de.sciss.fscape.stream.impl.{Real1FFTStageImpl, Real1FullFFTStageImpl, Real1FullIFFTStageImpl, Real1IFFTStageImpl}

/** Real (positive spectrum) forward Short Time Fourier Transform.
  * The counter-part of it is `Real1IFFT`.
  */
object Real1FFT {
  def apply(in: Outlet[BufD], size: Outlet[BufI], padding: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] =
    new Real1FFTStageImpl(ctrl).connect(in = in, size = size, padding = padding)
}

/** Real (positive spectrum) inverse Short Time Fourier Transform.
  * The counter-part of `Real1FFT`.
  */
object Real1IFFT {
  def apply(in: Outlet[BufD], size: Outlet[BufI], padding: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] =
    new Real1IFFTStageImpl(ctrl).connect(in = in, size = size, padding = padding)
}

/** Real (full spectrum) forward Short Time Fourier Transform.
  * This produces a symmetric spectrum (redundant) spectrum, whereas
  * `Real1FFT` only produces half of the spectrum.
  * The counter-part of it is `Real1FullIFFT`.
  */
object Real1FullFFT {
  def apply(in: Outlet[BufD], size: Outlet[BufI], padding: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] =
    new Real1FullFFTStageImpl(ctrl).connect(in = in, size = size, padding = padding)
}

/** Real (full spectrum) inverse Short Time Fourier Transform.
  * This is the counter-part to `Real1FullFFT`. It assumes
  * the input is a complex spectrum of a real signal, and after the IFFT
  * drops the imaginary part.
  */
object Real1FullIFFT {
  def apply(in: Outlet[BufD], size: Outlet[BufI], padding: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] =
    new Real1FullIFFTStageImpl(ctrl).connect(in = in, size = size, padding = padding)
}