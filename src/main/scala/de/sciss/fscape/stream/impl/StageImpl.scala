/*
 *  StageImpl.scala
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
package stream
package impl

import akka.stream.{Attributes, Shape}
import akka.stream.stage.GraphStage

abstract class StageImpl[S <: Shape](final protected val name: String)
  extends GraphStage[S] {

  override def toString = s"$name@${hashCode.toHexString}"

  override def initialAttributes = Attributes.name(toString)
}