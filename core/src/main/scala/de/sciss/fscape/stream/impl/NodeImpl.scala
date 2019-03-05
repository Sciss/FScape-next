/*
 *  StageLogicImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

abstract class NodeImpl[+S <: Shape](protected final val name: String,
                                     final val shape: S)
                                    (implicit final protected val control: Control)
  extends GraphStageLogic(shape) with Node {

  override def toString = s"$name-L@${hashCode.toHexString}"

  final def failAsync(ex: Exception): Unit = {
    val async = getAsyncCallback { _: Unit =>
      failStage(ex)
    }
    async.invoke(())
  }
}