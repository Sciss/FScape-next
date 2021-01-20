/*
 *  MatchLen.scala
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

object MatchLen extends ProductReader[MatchLen] {
  override def read(in: RefMapIn, key: String, arity: Int): MatchLen = {
    require (arity == 2)
    val _in   = in.readGE()
    val _ref  = in.readGE()
    new MatchLen(_in, _ref)
  }
}
/** A UGen that extends or truncates its first argument to match
  * the length of the second argument. If `in` is shorter than
  * `ref`, it will be padded with the zero element of its number type.
  * If `in` is longer, the trailing elements will be dropped.
  */
final case class MatchLen(in: GE, ref: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, ref.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, ref) = args
    import in.tpe
    val out = stream.MatchLen[in.A, in.Buf](in = in.toElem, ref = ref.toAny)
    tpe.mkStreamOut(out)
  }
}