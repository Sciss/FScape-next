/*
 *  DebugIntPromise.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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

object DebugIntPromise {
  def apply(in: OutI, p: Promise[Vec[Int]])(implicit b: Builder): Unit = {
    val stage0  = new Stage(p)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "DebugIntPromise"

  private type Shape = SinkShape[BufI]

  private final class Stage(p: Promise[Vec[Int]])(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = SinkShape(
      in = InI(s"$name.in")
    )

    def createLogic(attr: Attributes) = new Logic(p, shape)
  }

  private final class Logic(p: Promise[Vec[Int]], shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with Sink1Impl[BufI] {

    override def toString = s"$name@${hashCode().toHexString}"

    private[this] var builder = Vector.newBuilder[Int]

    override protected def stopped(): Unit = {
      builder = null
      p.tryFailure(new Exception("No orderly completion"))
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
      // println(s"DebugIntPromise($label).process(in $bufIn0, trig $bufIn1, chunk $stop0)")
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