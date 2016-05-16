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

import scala.language.implicitConversions

object UGenIn {
  implicit def fromInt   (x: Int   ): ConstantInt     = ConstantInt   (x)
  implicit def fromLong  (x: Long  ): ConstantLong    = ConstantLong  (x)
  implicit def fromDouble(x: Double): ConstantDouble  = ConstantDouble(x)
}
/** This is similar to `UGenIn` in ScalaCollider. */
trait UGenIn /* extends GE */ {
  // final private[fscape] def expand: UGenIn = this

  def readDouble(frames: Frames, off: Int, len: Int): Int
}