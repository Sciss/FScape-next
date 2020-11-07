/*
 *  Ops.scala
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

import scala.language.implicitConversions

object Ops extends Ops
trait Ops {
  implicit def geOps1      (g: GE    ): GEOps1 = new GEOps1(g)
  implicit def geOps2      (g: GE    ): GEOps2 = new GEOps2(g)
  implicit def intGeOps2   (i: Int   ): GEOps2 = new GEOps2(i)
  implicit def doubleGeOps2(d: Double): GEOps2 = new GEOps2(d)
}
