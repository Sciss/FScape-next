/*
 *  DebugPromise.scala
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

package de.sciss.fscape
package stream

import akka.stream.{Attributes, Inlet, Outlet, SinkShape}
import de.sciss.fscape.stream.impl.Handlers.InMain
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Promise

object DebugPromise {
  def apply[A, E <: BufElem[A], B >: A](in: Outlet[E], p: Promise[Vec[B]])
                                       (implicit b: Builder, tpe: StreamType[A, E]): Unit = {
    val stage0  = new Stage[A, E, B](b.layer, p)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "DebugPromise"

  private type Shp[E] = SinkShape[E]

  private final class Stage[A, E <: BufElem[A], B >: A](layer: Layer, p: Promise[Vec[B]])
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = SinkShape(
      in = Inlet[E](s"$name.in")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E, B](shape, layer, p)
  }

  private final class Logic[A, E <: BufElem[A], B >: A](shape: Shp[E], layer: Layer, p: Promise[Vec[B]])
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val inH     = InMain[A, E](this, shape.in)
    private[this] var builder = Vector.newBuilder[B]

    override protected def stopped(): Unit = {
      builder = null
//      p.trySuccess(Vector.empty)
      p.tryFailure(new Exception("No orderly completion"))
      super.stopped()
    }

    private def done(): Unit = {
      logStream.info(s"done() $this")
      p.success(builder.result())
      completeStage()
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      done()

    def process(): Unit = {
      while (true) {
        val rem = inH.available
        if (rem == 0) return

        var i = 0
        while (i < rem) {
          builder += inH.next()
          i += 1
        }

        if (inH.isDone) {
          done()
          return
        }
      }
    }
  }
}