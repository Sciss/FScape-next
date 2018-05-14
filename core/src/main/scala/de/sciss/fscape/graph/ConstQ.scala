/*
 *  ConstQ.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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

/** A UGen that performs a constant Q spectral analysis, using an already FFT'ed input signal.
  * For each input window, the output signal consists of `numBands` filter output values.
  * These values are '''squared''', since further processing might need to decimate them again
  * or take the logarithm. Otherwise the caller needs to add `.sqrt` to get the actual filter outputs.
  *
  * The default settings specify a range of nine octaves and `numBands = 432` thus being 48 bands
  * per octave. At 44.1 kHz, the default minimum frequency would be 35.28 Hz, the maximum would be
  * 18063.36 Hz.
  *
  * @param in         the input signal which has already been windowed and FFT'ed using `Real1FFT`
  *                   in mode 0 or 2.
  *                   The windows should have been rotated prior to the FFT so that the time
  *                   signal starts in the middle of the window and then wraps around.
  * @param fftSize    the fft size which corresponds with the window size of the input
  * @param minFreqN   the normalized minimum frequency (frequency in Hz, divided by the sampling rate).
  *                   should be greater than zero and less than or equal to 0.5.
  * @param maxFreqN   the normalized maximum frequency (frequency in Hz, divided by the sampling rate).
  *                   should be greater than zero and less than or equal to 0.5.
  * @param numBands   the number of bands or kernels to apply, spreading them evenly (logarithmically)
  *                   across the range from minimum to maximum frequency.
  */
final case class ConstQ(in: GE, fftSize: GE, minFreqN: GE = 0.0008, maxFreqN: GE = 0.4096, numBands: GE = 432)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, fftSize.expand, minFreqN.expand, maxFreqN.expand, numBands.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, fftSize, minFreqN, maxFreqN, numBands) = args
    stream.ConstQ(in = in.toDouble, fftSize = fftSize.toInt,
      minFreqN = minFreqN.toDouble, maxFreqN = maxFreqN.toDouble, numBands = numBands.toInt)
  }
}
