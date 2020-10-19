/*
 *  Loudness.scala
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

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A loudness measurement UGen, using Zwicker bands.
  * One value in Phon per window is output.
  *
  * The original algorithm outputs a
  * minimum value of 3.0. This is still the case, but if a window is entirely
  * silent (all values are zero), the output value is also 0.0. Thus one may
  * either distinguish between these two cases, or just treat output value
  * of `<= 3.0` as silences.
  *
  * @param  in          the signal to analyse
  * @param  sampleRate  sample rate of the input signal
  * @param  size        the window size for which to calculate values
  * @param  spl         the reference of 0 dBFS in decibels
  * @param  diffuse     whether to assume diffuse field (`1`) or free field (`0`)
  */
final case class Loudness(in: GE, sampleRate: GE, size: GE, spl: GE = 70, diffuse: GE = 1)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, sampleRate.expand, size.expand, spl.expand, diffuse.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, sampleRate, size, spl, diffuse) = args
    stream.Loudness(in = in.toDouble, sampleRate = sampleRate.toDouble, size = size.toInt,
      spl = spl.toDouble, diffuse = diffuse.toInt)
  }
}
