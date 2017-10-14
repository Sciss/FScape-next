/*
 *  MkDouble.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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
import de.sciss.fscape.stream.{BufD, Builder, Control, _}
import de.sciss.serial.{DataOutput, ImmutableSerializer}

object MkDouble {
  def apply(in: OutD, ref: OutputRef)(implicit b: Builder): Unit = {
    val stage0  = new Stage(ref)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "MkDouble"

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

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in)) {
          logStream(s"completeStage() $this")
          completeStage()
        }
        return
      }

      logStream(s"process() $this")

      val stop0   = readIns()
      val b0      = bufIn0.buf
      if (stop0 > 0) {
        val res = b0(0)
        ref.complete(new Output.Writer {
          def write(out: DataOutput): Unit =
            ImmutableSerializer.Double.write(res, out)
        })
        completeStage()
      }
    }
  }
}