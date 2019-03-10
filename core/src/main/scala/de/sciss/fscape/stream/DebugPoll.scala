/*
 *  DebugPoll.scala
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
package stream

import akka.stream.{Attributes, Inlet, Outlet, SinkShape}
import de.sciss.fscape.stream.impl.{NodeImpl, Sink1Impl, StageImpl}

// XXX TODO --- we could use an `Outlet[String]`, that might be making perfect sense
object DebugPoll {
  def apply(in: Outlet[BufLike], label: String)(implicit b: Builder): Unit = {
    // println(s"DebugPoll($in, $trig, $label)")
    val stage0  = new Stage(layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "DebugPoll"

  private type Shape = SinkShape[BufLike]

  private final class Stage(layer: Layer, label: String)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = SinkShape(
      in = Inlet[BufLike](s"$name.in")
    )

    def createLogic(attr: Attributes) = new Logic(shape = shape, layer = layer, label = label)
  }

  private final class Logic(shape: Shape, layer: Layer, label: String)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with Sink1Impl[BufLike] {

    override def toString = s"$name-L($label)"

    private[this] var high0 = false

    def process(): Unit = {
      if (!canRead) {
        if (isClosed(shape.in) && !isAvailable(shape.in)) {
          logStream(s"completeStage() $this")
          completeStage()
        }
        return
      }

      logStream(s"process() $this")

      val stop0   = readIns()
      // println(s"DebugPoll($label).process(in $bufIn0, trig $bufIn1, chunk $stop0)")
      // bufIn0.assertAllocated()
      // println(s"poll   : $bufIn0 | ${bufIn0.allocCount()}")

      val b0: Array[_] = bufIn0.buf
      var h0      = high0
      var h1      = h0
      var inOffI  = 0
      while (inOffI < stop0) {
        h1 = true
        if (h1 && !h0) {
          val x0 = b0(inOffI)
          // XXX TODO --- make console selectable
          println(s"$label: $x0")
        }
        inOffI  += 1
        h0       = h1
      }
      high0 = h0
    }
  }
}