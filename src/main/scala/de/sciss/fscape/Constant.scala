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

case class ConstantInt   (i: Int)    extends UGenIn
case class ConstantLong  (n: Long)   extends UGenIn
case class ConstantDouble(d: Double) extends UGenIn
