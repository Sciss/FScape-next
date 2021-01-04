/*
 *  DebugThrough.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object DebugThrough {
  def apply[A, Buf <: BufElem[A]](in: Outlet[Buf], label: String)(implicit b: Builder): Outlet[Buf] = {
    // println(s"DebugThrough($in, $trig, $label)")
    val stage0  = new Stage[A, Buf](layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "DebugThrough"

  private type Shp[E] = FlowShape[E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, label: String)(implicit ctrl: Control)
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = FlowShape(
      in  = Inlet [E](s"$name.in"),
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape = shape, layer = layer, label = label)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, label: String)
                                                       (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler with OutHandler { logic =>

    override def toString = s"$name-L($label)"

    private[this] var framesSeen = 0L

    def onPush(): Unit =
      if (isAvailable(shape.out )) process()

    def onPull(): Unit =
      if (isAvailable(shape.in  )) process()

    override def onUpstreamFinish(): Unit = {
      val ok = isAvailable(shape.in)
      if (!ok) {
        println(s"$label: onUpstreamFinish.   frames = $framesSeen") // ; avail? ${isAvailable(shape.in)}")
        super.onUpstreamFinish()
      }
    }

    override def onDownstreamFinish(cause: Throwable): Unit = {
//      val ok = isAvailable(shape.out)
//      if (!ok) {
        println(s"$label: onDownstreamFinish. frames = $framesSeen") // ; avail? ${isAvailable(shape.out)}")
        super.onDownstreamFinish(cause)
//      }
    }

    setHandler(shape.in , this)
    setHandler(shape.out, this)

    private def process(): Unit = {
      val buf = grab(shape.in)
      framesSeen += buf.size
      push(shape.out, buf)
      if      (isClosed(shape.in  )) onUpstreamFinish()
//      else if (isClosed(shape.out )) onDownstreamFinish()
      else pull(shape.in)
    }
  }
}