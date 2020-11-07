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
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.collection.mutable

object Distinct {
  def apply[A, E <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Distinct"

  private type Shp[E] = FlowShape[E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet [E](s"$name.in"),
      out = Outlet[E](s"$name.out"),
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit control: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val seen    = mutable.Set.empty[A]

    private[this] val hIn : InMain  [A, E]  = InMain [A, E](this, shape.in )
    private[this] val hOut: OutMain [A, E]  = OutMain[A, E](this, shape.out)

    override protected def stopped(): Unit = {
      super.stopped()
      seen.clear()
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) {
        completeStage()
      }

    protected def process(): Unit = {
      logStream.debug(s"$this process()")

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