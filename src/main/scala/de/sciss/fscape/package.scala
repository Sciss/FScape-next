/*
 *  package.scala
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

package de.sciss

import scala.language.implicitConversions

package object fscape {
  implicit def geOps(g: GE): GEOps = new GEOps(g)
}