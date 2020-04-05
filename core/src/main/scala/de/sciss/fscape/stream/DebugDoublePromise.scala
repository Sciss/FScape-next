/*
 *  DebugDoublePromise.scala
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

import akka.stream.{Attributes, SinkShape}
import de.sciss.fscape.stream.impl.{NodeImpl, Sink1Impl, StageImpl}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Promise

object DebugDoublePromise {
  def apply(in: OutD, p: Promise[Vec[Double]])(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, p)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "DebugDoublePromise"

  private type Shp = SinkShape[BufD]

  private final class Stage(layer: Layer, p: Promise[Vec[Double]])(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = SinkShape(
      in = InD(s"$name.in")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, p)
  }

  private final class Logic(shape: Shp, layer: Layer, p: Promise[Vec[Double]])(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with Sink1Impl[BufD] {

    override def toString = s"$name@${hashCode().toHexString}"

    private[this] var builder = Vector.newBuilder[Double]

    override protected def stopped(): Unit = {
      builder = null
      p.trySuccess(Vector.empty) // p.tryFailure(new Exception("No orderly completion"))
      super.stopped()
    }

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in) && !isAvailable(shape.in)) {
          logStream(s"completeStage() $this")
          p.success(builder.result())
          completeStage()
        }
        return
      }

      logStream(s"process() $this")

      val stop0   = readIns()
      // println(s"DebugDoublePromise($label).process(in $bufIn0, trig $bufIn1, chunk $stop0)")
      // bufIn0.assertAllocated()
      // println(s"poll   : $bufIn0 | ${bufIn0.allocCount()}")

      val b0        = bufIn0.buf
      val _builder  = builder
      var inOffI  = 0
      while (inOffI < stop0) {
        val x0 = b0(inOffI)
        _builder += x0
        inOffI  += 1
      }
    }
  }
}