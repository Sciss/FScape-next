/*
 *  Metro.scala
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
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn2IImpl, StageImpl, NodeImpl}

object Metro {
  def apply(period: OutL, phase: OutL)(implicit b: Builder): OutI = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(period, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "Metro"

  private type Shape = FanInShape2[BufL, BufL, BufI]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InL (s"$name.period"),
      in1 = InL (s"$name.phase"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with GenChunkImpl[BufL, BufI, Shape]
      with GenIn2IImpl[BufL, BufL] {

    private[this] var period  : Long = _
    private[this] var phase   : Long = _    // internal state; does not include `phaseOff`
    private[this] var init = true

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      // println(s"Metro.processChunk($bufIn0, $chunk)")

      var inOffI    = inOff
      var outOffI   = outOff
      val stop      = inOffI + chunk
      val b0        = if (bufIn0 == null) null else bufIn0.buf
      val out       = bufOut0.buf
      val stop0     = if (b0 == null) 0 else bufIn0.size

      var periodV   = period
      var phaseV    = phase

      if (init) {
        val b1        = bufIn1.buf
        periodV       = b0(inOffI)
        if (periodV == 0) periodV = Long.MaxValue
        val phaseOffV = b1(inOffI)
        phaseV        = phaseOffV + periodV
        init          = false
      }

      while (inOffI < stop) {
        if (inOffI < stop0) {
          periodV = b0(inOffI)
          if (periodV == 0) periodV = Long.MaxValue
        }
        if (phaseV >= periodV) {
          phaseV %= periodV
          out(outOffI)  = 1
        } else {
          out(outOffI)  = 0
        }
        phaseV       += 1
        inOffI       += 1
        outOffI      += 1
      }
      period    = periodV
      phase     = phaseV
    }
  }
}