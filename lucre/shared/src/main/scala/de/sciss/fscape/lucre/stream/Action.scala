/*
 *  Action.scala
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
package lucre.stream

import akka.stream.{Attributes, Inlet, SinkShape}
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream.impl.Handlers.InIMain
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufI, Builder, Control, _}

import scala.annotation.tailrec

object Action {
  def apply(gate: OutI, ref: Input.Action.Value)(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, ref)
    val stage   = b.add(stage0)
    b.connect(gate, stage.in)
  }

  private final val name = "Action"

  private type Shp = SinkShape[BufI]

  private final class Stage(layer: Layer, ref: Input.Action.Value)(implicit ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = new SinkShape(
      in = InI(s"$name.trig")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, ref)
  }

  private final class Logic(shape: Shp, layer: Layer, ref: Input.Action.Value)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    protected val hIn: InIMain = InIMain(this, shape.in)

    protected def onDone(inlet: Inlet[_]): Unit =
      completeStage()

    @tailrec
    protected def process(): Unit = {
      val rem = hIn.available
      if (rem == 0) return

      val in        = hIn.array
      var inOff0    = hIn.offset
      val stop0     = inOff0 + rem
      while (inOff0 < stop0) {
        val gate = in(inOff0) > 0
        if (gate) {
          ref.execute(None)
        }
        inOff0 += 1
      }
      hIn.advance(rem)

      if (hIn.isDone) {
        completeStage()
      } else {
        process()
      }
    }
  }
}