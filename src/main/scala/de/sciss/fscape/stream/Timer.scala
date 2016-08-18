/*
 *  Timer.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1LImpl, StageImpl, StageLogicImpl}

object Timer {
  def apply(trig: OutI)(implicit b: Builder): OutL = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(trig, stage.in)
    stage.out
  }

  private final val name = "Timer"

  private type Shape = FlowShape[BufI, BufL]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FlowShape(
      in  = InI (s"$name.trig"),
      out = OutL(s"$name.out" )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with FilterIn1LImpl[BufI]
      with FilterChunkImpl[BufI, BufL, Shape] {

    private[this] var high      = false
    private[this] var count     = 0L

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0.buf
      val out     = bufOut0.buf
      var h0      = high
      var h1      = false
      var c0      = count
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        h1 = b0(inOffI) > 0
        if (h1 && !h0) {
          println(s"RESET FROM $c0")
          c0 = 0L
        }
        out(outOffI) = c0
        inOffI  += 1
        outOffI += 1
        c0      += 1
        h0       = h1
      }
      high  = h0
      count = c0
    }
  }
}