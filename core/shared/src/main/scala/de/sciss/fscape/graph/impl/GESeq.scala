/*
 *  GESeq.scala
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
package impl

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}

import scala.collection.immutable.{IndexedSeq => Vec}

object GESeq extends ProductReader[GESeq] {
  override def read(in: RefMapIn, key: String, arity: Int): GESeq = {
    require (arity == 1)
    val _in = in.readVec(in.readGE())
    new GESeq(_in)
  }
}
final case class GESeq(elems: Vec[GE]) extends GE {
  private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike =
    UGenInGroup(elems.map(_.expand))

  override def toString: String = elems.mkString("GESeq(", ",", ")")
}