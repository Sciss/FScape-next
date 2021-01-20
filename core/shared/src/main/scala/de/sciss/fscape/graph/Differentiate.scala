/*
 *  Differentiate.scala
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

object Differentiate extends ProductReader[Differentiate] {
  override def read(in: RefMapIn, key: String, arity: Int): Differentiate = {
    require (arity == 1)
    val _in = in.readGE()
    new Differentiate(_in)
  }
}
/** A UGen that outputs the differences between
  * adjacent input samples.
  *
  * The length of the signal is the length of the input.
  * The first output will be the first input (internal
  * x1 state is zero).
  */
final case class Differentiate(in: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    import in.tpe
    val out = stream.Differentiate[in.A, in.Buf](in = in.toElem)
    tpe.mkStreamOut(out)
  }
}