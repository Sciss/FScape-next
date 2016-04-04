/*
 *  UGenIn.scala
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

/** This is similar to `UGenIn` in ScalaCollider. */
trait UGenIn extends GE {
  final private[fscape] def expand: UGenIn = this
}
