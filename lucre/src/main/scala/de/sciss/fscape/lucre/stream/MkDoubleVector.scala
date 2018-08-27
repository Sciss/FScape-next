/*
 *  MkDoubleVector.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.lucre.expr.DoubleVector
import de.sciss.serial.DataOutput

import scala.collection.immutable.{IndexedSeq => Vec}

object MkDoubleVector {
  def apply(in: OutD, ref: OutputRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(ref)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "MkDoubleVector"

  private type Shape = SinkShape[BufD]

  private final class Stage(ref: OutputRef)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new SinkShape(
      in = InD(s"$name.in")
    )

    def createLogic(attr: Attributes) = new Logic(shape, ref)
  }

  private final class Logic(shape: Shape, ref: OutputRef)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with Sink1Impl[BufD] {

    private[this] val builder = Vec.newBuilder[Double]

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in)) {
          val res = builder.result()
          ref.complete(new Output.Writer {
            def write(out: DataOutput): Unit =
              DoubleVector.valueSerializer.write(res, out)
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