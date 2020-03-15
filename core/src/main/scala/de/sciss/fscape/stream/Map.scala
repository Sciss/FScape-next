/*
 *  Map.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.{logStream, stream}

object Map {
  def apply[A, B](in: Outlet[A], name: String)(fun: A => B)(implicit b: stream.Builder): Outlet[B] = {
    val stage0  = new Stage[A, B](name, fun)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private type Shape[A, B] = FlowShape[A, B]

  private final class Stage[A, B](name: String, fun: A => B) extends GraphStage[Shape[A, B]] {
    override def toString = s"$name@${hashCode().toHexString}"

    val shape: Shape = FlowShape[A, B](
      in  = Inlet [A](s"$name.in"),
      out = Outlet[B](s"$name.out")
    )

    override def initialAttributes: Attributes = Attributes.name(toString)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic[A, B](name, shape, fun)
  }

  private final class Logic[A, B](name: String, shape: Shape[A, B], fun: A => B)
    extends GraphStageLogic(shape) with InHandler with OutHandler { self =>

    override def toString = s"$name@${hashCode().toHexString}"

    import shape.{in, out}

    override def onPush(): Unit = {
      logStream(s"onPush() $self")
      val a = grab(in)
      val b = fun(a)
      push(out, b)
    }

    override def onPull(): Unit = {
      logStream(s"onPull() $self")
      pull(in)
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish() $self")
      super.onUpstreamFinish()
    }

    override def onDownstreamFinish(cause: Throwable): Unit = {
      logStream(s"onDownstreamFinish() $self")
      super.onDownstreamFinish(cause)
    }

    setHandlers(in, out, this)
  }
}
