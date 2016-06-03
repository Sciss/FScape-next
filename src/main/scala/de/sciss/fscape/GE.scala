/*
 *  GE.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import de.sciss.fscape.graph.{ConstantD, ConstantI}

import scala.language.implicitConversions

object GE {
  trait Lazy extends Lazy.Expander[UGenInLike] with GE

  implicit def fromInt   (x: Int   ): ConstantI = new ConstantI(x)
  implicit def fromDouble(x: Double): ConstantD = new ConstantD(x)

  implicit def fromSeq(xs: scala.Seq[GE]): GE = xs match {
    case scala.Seq(x) => x
    case _            => graph.impl.GESeq(xs.toIndexedSeq)
  }
}
trait GE extends Product {
  private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike
}