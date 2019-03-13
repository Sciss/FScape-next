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

import scala.concurrent.{ExecutionContext, Future}

abstract class NodeImpl[+S <: Shape](protected final val name: String, val layer: Layer,
                                     final val shape: S)
                                    (implicit final protected val control: Control)
  extends GraphStageLogic(shape) with Node {

  override def toString = s"$name-L@${hashCode.toHexString}"

  final def launchAsync(): Future[Unit] = {
    val async = getAsyncCallback { _: Unit =>
      logStream(s"$this - launchAsync")
      shape.inlets.foreach(pull(_))
    }

    implicit val ex: ExecutionContext = control.config.executionContext
    async.invokeWithFeedback(()).map(_ => ())
  }

  final def failAsync(ex: Exception): Unit = {
    val async = getAsyncCallback { _: Unit =>
      failStage(ex)
    }
    async.invoke(())
  }

  def completeAsync(): Future[Unit] = {
    val async = getAsyncCallback { _: Unit =>
      logStream(s"$this - completeAsync")
      completeStage()
    }

    implicit val ex: ExecutionContext = control.config.executionContext
    async.invokeWithFeedback(()).map(_ => ())
  }
}

trait NodeHasInitImpl extends NodeHasInit {
  _: GraphStageLogic =>

  protected def init(): Unit = ()

  final def initAsync(): Future[Unit] = {
    val async = getAsyncCallback { _: Unit =>
      logStream(s"$this - initAsync")
      init()
    }

    implicit val ex: ExecutionContext = control.config.executionContext
    async.invokeWithFeedback(()).map(_ => ())
  }
}