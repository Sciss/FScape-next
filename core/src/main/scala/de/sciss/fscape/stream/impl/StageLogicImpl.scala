/*
 *  StageLogicImpl.scala
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

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

abstract class StageLogicImpl[S <: Shape](protected final val name: String,
                                          protected final val shape: S)
                                         (implicit final protected val ctrl: Control)
  extends GraphStageLogic(shape) {

  override def toString = s"$name-L@${hashCode.toHexString}"
}
