/*
 *  Timer.scala
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

import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1LImpl, StageImpl, NodeImpl}

object Timer {
  def apply(trig: OutI)(implicit b: Builder): OutL = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(trig, stage.in)
    stage.out
  }

  private final val name = "Timer"

  private type Shp = FlowShape[BufI, BufL]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FlowShape(
      in  = InI (s"$name.trig"),
      out = OutL(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn1LImpl[BufI]
      with FilterChunkImpl[BufI, BufL, Shp] {

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
          // println(s"RESET FROM $c0")
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