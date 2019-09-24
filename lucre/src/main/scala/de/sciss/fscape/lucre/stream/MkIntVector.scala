/*
 *  MkIntVector.scala
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
package lucre.stream

import akka.stream.{Attributes, SinkShape}
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.impl.{NodeImpl, Sink1Impl, StageImpl}
import de.sciss.fscape.stream.{Builder, Control, _}
import de.sciss.lucre.expr.IntVector
import de.sciss.serial.DataOutput

import scala.collection.immutable.{IndexedSeq => Vec}

object MkIntVector {
  def apply(in: OutI, ref: OutputRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, ref)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "MkIntVector"

  private type Shape = SinkShape[BufI]

  private final class Stage(layer: Layer, ref: OutputRef)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new SinkShape(
      in = InI(s"$name.in")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer, ref)
  }

  private final class Logic(shape: Shape, layer: Layer, ref: OutputRef)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with Sink1Impl[BufI] {

    private[this] val builder = Vec.newBuilder[Int]

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in)) {
          ref.complete(new Output.Writer {
            override val outputValue: Vec[Int] = builder.result()

            def write(out: DataOutput): Unit =
              IntVector.valueSerializer.write(outputValue, out)
          })
          completeStage()
        }
        return
      }

      logStream(s"process() $this")

      val stop0     = readIns()
      val b0        = bufIn0.buf
      val _builder  = builder
      var inOff0    = 0
      while (inOff0 < stop0) {
        _builder += b0(inOff0)
        inOff0 += 1
      }
    }
  }
}