/*
 *  MelFilter.scala
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

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that maps short-time Fourier transformed spectra to the mel scale. To obtain
  * the MFCC, one has to take the log of the output of this UGen and decimate it with a `DCT`.
  *
  * Example:
  * {{{
  * def mfcc(in: GE) = {
  *   val fsz  = 1024
  *   val lap  = Sliding(in, fsz, fsz/2) * GenWindow(fsz, GenWindow.Hann)
  *   val fft  = Real1FFT(lap, fsz, mode = 1)
  *   val mag  = fft.complex.mag.max(-80)
  *   val mel  = MelFilter(mag, fsz/2, bands = 42)
  *   DCT_II(mel.log, 42, 13, zero = 0)
  * }
  * }}}
  *
  * @param in         magnitudes of spectra, as output by `Real1FFT(..., mode = 1).complex.abs`
  * @param size       bands in input spectrum (assumed to be `fft-size / 2`).
  *                   lowest band corresponds to DC and highest to `(size - 1)/size * sampleRate/2`.
  * @param minFreq    lower frequency to sample. Will be clipped between zero (inclusive) and Nyquist (exclusive).
  * @param maxFreq    upper frequency to sample. Will be clipped between `minFreq` (inclusive) and Nyquist (exclusive).
  * @param bands      number of filter bands output
  */
final case class MelFilter(in: GE, size: GE, minFreq: GE = 55.0, maxFreq: GE = 18000.0, sampleRate: GE = 44100.0,
                           bands: GE = 42) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, minFreq.expand, maxFreq.expand, sampleRate.expand, bands.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, minFreq, maxFreq, sampleRate, bands) = args
    stream.MelFilter(in = in.toDouble, size = size.toInt, minFreq = minFreq.toDouble, maxFreq = maxFreq.toDouble,
      sampleRate = sampleRate.toDouble, bands = bands.toInt)
  }
}
