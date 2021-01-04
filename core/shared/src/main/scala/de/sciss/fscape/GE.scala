/*
 *  GE.scala
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

import de.sciss.fscape.graph.impl.GESeq
import de.sciss.fscape.graph.{ConstantD, ConstantI, ConstantL}

import scala.language.implicitConversions

object GE {
  trait Lazy extends Lazy.Expander[UGenInLike] with GE

  implicit def fromInt   (x: Int   ): ConstantI = new ConstantI(x)
  implicit def fromDouble(x: Double): ConstantD = new ConstantD(x)
  implicit def fromLong  (x: Long  ): ConstantL = new ConstantL(x)

  implicit def fromSeq(xs: scala.Seq[GE]): GE = xs match {
    case scala.Seq(x) => x
    case _            => GESeq(xs.toIndexedSeq)
  }
}
/** The main trait used in an FScape graph, a graph element, abbreviated as `GE`.
  *
  * A lot of operations on `GE` are defined separately in `GEOps1` and `GEOps2`
  *
  * @see [[GEOps1]]
  * @see [[GEOps2]]
  */
trait GE extends Product {
  private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike
}