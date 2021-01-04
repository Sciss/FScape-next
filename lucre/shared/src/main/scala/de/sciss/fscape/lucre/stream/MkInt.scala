/*
 *  MkInt.scala
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
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.impl.Handlers.InIMain
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufI, Builder, Control, _}
import de.sciss.serial.{DataOutput, TFormat}
import de.sciss.proc.FScape.Output

object MkInt {
  def apply(in: OutI, ref: OutputRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, ref)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "MkInt"

  private type Shp = SinkShape[BufI]

  private final class Stage(layer: Layer, ref: OutputRef)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new SinkShape(
      in = InI(s"$name.in")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, ref)
  }

  private final class Logic(shape: Shp, layer: Layer, ref: OutputRef)(implicit ctrl: Control)
    extends Handlers(name, layer, shape)  {

    protected val hIn: InIMain = InIMain(this, shape.in)

    protected def onDone(inlet: Inlet[_]): Unit =
      completeStage()

    protected def process(): Unit = {
      if (hIn.hasNext) {
        val v = hIn.next()
        ref.complete(new Output.Writer {
          override val outputValue: Int = v

          def write(out: DataOutput): Unit =
            TFormat.Int.write(outputValue, out)
        })
        completeStage()
      }
    }
  }
}