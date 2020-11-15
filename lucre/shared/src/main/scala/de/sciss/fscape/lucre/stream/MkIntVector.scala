/*
 *  MkIntVector.scala
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
package lucre.stream

import akka.stream.{Attributes, Inlet, SinkShape}
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.impl.Handlers.InIMain
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{Builder, Control, _}
import de.sciss.lucre.IntVector
import de.sciss.serial.DataOutput
import de.sciss.synth.proc.FScape.Output

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

object MkIntVector {
  def apply(in: OutI, ref: OutputRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, ref)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "MkIntVector"

  private type Shp = SinkShape[BufI]

  private final class Stage(layer: Layer, ref: OutputRef)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new SinkShape(
      in = InI(s"$name.in")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, ref)
  }

  private final class Logic(shape: Shp, layer: Layer, ref: OutputRef)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    type A = Int

    private[this] val builder = Vec.newBuilder[A]

    protected val hIn: InIMain = InIMain(this, shape.in)

    protected def onDone(inlet: Inlet[_]): Unit = finish()

    private def finish(): Unit = {
      val v = builder.result()
      ref.complete(new Output.Writer {
        override val outputValue: Vec[A] = v

        def write(out: DataOutput): Unit =
          IntVector.valueFormat.write(outputValue, out)
      })
      completeStage()
    }

    @tailrec
    protected def process(): Unit = {
      val rem = hIn.available
      if (rem == 0) return

      val in        = hIn.array
      var inOff0    = hIn.offset
      val stop0     = inOff0 + rem
      val _builder  = builder
      while (inOff0 < stop0) {
        _builder += in(inOff0)
        inOff0 += 1
      }
      hIn.advance(rem)

      if (hIn.isDone) {
        finish()
      } else {
        process()
      }
    }
  }
}