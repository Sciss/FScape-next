/*
 *  MelFilter.scala
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

import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that maps short-time Fourier transformed spectra to the mel scale. To obtain
  * the MFCC, one has to take the log of the output of this UGen and decimate it with a `DCT`.
  *
  * Example:
  * {{{
  * def mfcc(in: GE) = {
  *   val fsz  = 1024
  *   val lap  = Sliding(in, fsz, fsz/2) * GenWindow
  *   val fft  = Real1FFT(in, fsz)
  *   val mag  = fft.complex.mag
  *   val mel  = MelFilter(mag, fsz/2+1, bands = 42)
  *   DCT(mel.log, 42, 13)
  * }
  * }}}
  *
  * @param in         magnitudes of spectra, as output by `Real1FFT(...).complex.abs`
  * @param size       bands in input spectrum (typically `fft-size / 2 + 1`).
  *                   lowest band corresponds to DC and highest to Nyquist.
  * @param minFreq    lower frequency to sample.
  * @param maxFreq    upper frequency to sample.
  * @param bands      number of filter bands output
  */
final case class MelFilter(in: GE, size: GE, minFreq: GE = 55.0, maxFreq: GE = 18000.0, sampleRate: GE = 44100.0,
                           bands: GE = 42) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(in.expand, size.expand, minFreq.expand, maxFreq.expand, sampleRate.expand, bands.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, minFreq, maxFreq, sampleRate, bands) = args
    stream.MelFilter(in = in.toDouble, size = size.toInt, minFreq = minFreq.toDouble, maxFreq = maxFreq.toDouble,
      sampleRate = sampleRate.toDouble, bands = bands.toInt)
  }
}
