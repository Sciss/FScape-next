/*
 *  StageImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.stage.GraphStage
import akka.stream.{Attributes, Shape}

abstract class StageImpl[S <: Shape](final protected val name: String)
  extends GraphStage[S] {

  override def toString = s"$name@${hashCode.toHexString}"

  override def initialAttributes: Attributes = Attributes.name(toString)

  /** We ensure that we use the more specific implementation class,
    * because it registers with the control. */
  override def createLogic(attr: Attributes): NodeImpl[S]
}
