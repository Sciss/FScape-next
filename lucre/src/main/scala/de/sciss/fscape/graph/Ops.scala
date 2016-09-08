/*
 *  Ops.scala
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
package graph

import scala.language.implicitConversions

object Ops {
  /** Allows the construction of attribute controls, for example via `"freq".kr`. */
  implicit def stringToControl(name: String): Attribute.Factory =
  new Attribute.Factory(name)
}
