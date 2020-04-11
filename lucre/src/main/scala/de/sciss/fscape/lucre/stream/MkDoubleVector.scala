/*
 *  MkDoubleVector.scala
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

import akka.stream.{Attributes, SinkShape}
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.impl.deprecated.Sink1Impl
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.stream.{Builder, Control, _}
import de.sciss.lucre.expr.DoubleVector
import de.sciss.serial.DataOutput

import scala.collection.immutable.{IndexedSeq => Vec}

object MkDoubleVector {
  def apply(in: OutD, ref: OutputRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, ref)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "MkDoubleVector"

  private type Shp = SinkShape[BufD]

  private final class Stage(layer: Layer, ref: OutputRef)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new SinkShape(
      in = InD(s"$name.in")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, ref)
  }

  private final class Logic(shape: Shp, layer: Layer, ref: OutputRef)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with Sink1Impl[BufD] {

    private[this] val builder = Vec.newBuilder[Double]

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in)) {
          ref.complete(new Output.Writer {
            override val outputValue: Vec[Double] = builder.result()

            def write(out: DataOutput): Unit =
              DoubleVector.valueSerializer.write(outputValue, out)
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