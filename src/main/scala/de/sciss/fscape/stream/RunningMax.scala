/*
 *  RunningMax.scala
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

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2Impl, Out1LogicImpl, StageLogicImpl}

object RunningMax {
  def apply(in: OutD, trig: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(trig, stage.in1)
    stage.out
  }

  private final val name = "RunningMax"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends GraphStage[Shape] {

    override def toString = s"$name@${hashCode.toHexString}"

    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with FilterChunkImpl[BufD, BufD, Shape]
      with FilterIn2Impl[BufD, BufI, BufD]
      with Out1LogicImpl[BufD, Shape] {

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    private[this] var value = Double.NegativeInfinity
    private[this] var trig0 = false

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Int = {
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOffI + chunk
      val b0      = bufIn0.buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val out     = bufOut0.buf
      val stop1   = if (b1 == null) 0 else bufIn1.size
      var v       = value
      var t0      = trig0
      var t1      = t0
      while (inOffI < stop0) {
        val x0 = b0(inOffI)
        if (inOffI < stop1) t1 = !t0 && b1(inOffI) > 0
        v = if (t1) x0 else math.max(v, x0)
        out(outOffI) = v
        inOffI  += 1
        outOffI += 1
        t0       = t1
      }
      value = v
      trig0 = t0
      chunk
    }
  }
}