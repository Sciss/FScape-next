/*
 *  Poll.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.Attributes
import akka.stream.stage.{GraphStage, GraphStageLogic}
import de.sciss.fscape.stream.impl.{Sink2Impl, SinkShape2}

// XXX TODO --- we could use an `Outlet[String]`, that might be making perfect sense
object Poll {
  def apply(in: OutD, trig: OutI, label: String)(implicit b: Builder): Unit = {
    // println(s"Poll($in, $trig, $label)")
    val stage0  = new Stage(label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
  }

  private final class Stage(label: String)(implicit ctrl: Control)
    extends GraphStage[SinkShape2[BufD, BufI]] {

    val name = "Poll"

    override def toString = s"$name($label)"

    val shape = SinkShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(label = label, shape = shape)
  }

  private final class Logic(label: String, protected val shape: SinkShape2[BufD, BufI])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with Sink2Impl[BufD, BufI] {

    private[this] var high0 = false

    def process(): Unit = {
      if (!canRead) return

      val stop0   = readIns()
      // println(s"Poll($label).process(in $bufIn0, trig $bufIn1, chunk $stop0)")
      // bufIn0.assertAllocated()

      val b0      = bufIn0.buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      var h0      = high0
      var h1      = h0
      var inOffI  = 0
      while (inOffI < stop0) {
        if (inOffI < stop1) h1 = b1(inOffI) > 0
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