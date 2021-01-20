/*
 *  Timer.scala
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
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object Timer extends ProductReader[Timer] {
  override def read(in: RefMapIn, key: String, arity: Int): Timer = {
    require (arity == 1)
    val _trig = in.readGE()
    new Timer(_trig)
  }
}
/** A UGen that outputs the number of sample frames passed since last triggered.
  * If no trigger is used, it simply outputs a linearly rising ramp.
  *
  * @param  trig  trigger signal to reset the counter. Note that the UGen
  *               shuts down when `trig` finishes, so to use a constant like `0`,
  *               it has to be wrapped in a `DC`, for example.
  */
final case class Timer(trig: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(trig.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(trig) = args
    stream.Timer(trig = trig.toInt)
  }
}
