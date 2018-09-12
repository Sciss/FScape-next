/*
 *  Limiter.scala
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

/** A UGen that limits the amplitude of its input signal. Modelled after FScape 1.0 limiter module.
  * N.B.: The UGen outputs a gain control signal, so the input must be multiplied
  * by this signal to obtain the actually limited signal.
  *
  * @param    in      input signal
  * @param    attack  the attack  duration in frames, as -60 dB point
  * @param    release the release duration in frames, as -60 dB point
  * @param    ceiling the maximum allowed amplitude
  */
final case class Limiter(in: GE, attack: GE, release: GE, ceiling: GE = 1.0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, attack.expand, release.expand, ceiling.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, attack, release, ceiling) = args
    stream.Limiter(in = in.toDouble, attack = attack.toInt, release = release.toInt, ceiling = ceiling.toDouble)
  }
}