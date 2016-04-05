/*
 *  Constant.scala
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

sealed trait Constant extends GE with UGenIn {
  private[fscape] def expand: UGenIn = this

  protected def toDouble: Double

  final def readDouble(frames: Frames, off: Int, len: Int): Int = {
    Util.fill(frames, off, len, toDouble)
    len
  }
}
final case class ConstantInt   (i: Int)    extends Constant { def toDouble = i.toDouble }
final case class ConstantLong  (n: Long)   extends Constant { def toDouble = n.toDouble }
final case class ConstantDouble(d: Double) extends Constant { def toDouble = d          }
