/*
 *  DebugPoll.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that prints snapshots of its input to the console.
  * Note that arguments have different order than in ScalaCollider!
  *
  * @param in     the input to be pulled. If this is a constant,
  *               the UGen will close after polling it. This is to
  *               prevent a dangling `DebugPoll` whose trigger is
  *               infinite (such as `Impulse`). If you want to avoid
  *               that, you should wrap the input in a `DC`.
  * @param label  an identifying label to prepend to the printing.
  */
final case class DebugPoll(in: GE, label: String = "poll") extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args, aux = Aux.String(label) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in) = args
    stream.DebugPoll(in = in.toAny, label = label)
  }
}