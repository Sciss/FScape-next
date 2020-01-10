/*
 *  GESeq.scala
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
package impl

import scala.collection.immutable.{IndexedSeq => Vec}

final case class GESeq(elems: Vec[GE]) extends GE {
  private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike =
    UGenInGroup(elems.map(_.expand))

  override def toString: String = elems.mkString("GESeq(", ",", ")")
}