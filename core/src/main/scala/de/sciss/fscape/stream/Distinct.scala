/*
 *  Distinct.scala
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

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{Handlers, StageImpl}
import de.sciss.fscape.{logStream => log}

import scala.collection.mutable

object Distinct {
  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Distinct"

  private type Shape[A, E >: Null <: BufElem[A]] = FlowShape[E, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shape[A, E]](name) {

    val shape = new FlowShape(
      in  = Inlet [E](s"$name.in"),
      out = Outlet[E](s"$name.out"),
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], layer: Layer)
                                                       (implicit control: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val seen    = mutable.Set.empty[A]

    private[this] val hIn     = new Handlers.InMain [A, E](this, shape.in)()
    private[this] val hOut    = new Handlers.OutMain[A, E](this, shape.out)

    override protected def stopped(): Unit = {
      super.stopped()
      seen.clear()
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) {
        completeStage()
      }

    protected def process(): Unit = {
      log(s"$this process()")

      while (hIn.hasNext && hOut.hasNext) {
        val v     = hIn.next()
        val isNew = seen.add(v)
        if (isNew) hOut.next(v)
      }

      if (hIn.isDone && hOut.flush()) {
        completeStage()
      }
    }
  }
}