/*
 *  Ops.scala
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

package de.sciss.fscape.lucre.graph

import de.sciss.fscape

import scala.language.implicitConversions

object Ops extends fscape.Ops {
  /** Allows the construction of attribute controls, for example via `"freq".attr`. */
  implicit def stringToControl(name: String): Attribute.Factory =
    new Attribute.Factory(name)
}
