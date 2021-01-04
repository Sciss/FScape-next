/*
 *  Metro.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.min

object Metro {
  def apply(period: OutL, phase: OutL)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(period, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "Metro"

  private type Shp = FanInShape2[BufL, BufL, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InL (s"$name.period"),
      in1 = InL (s"$name.phase"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hPeriod = Handlers.InLAux   (this, shape.in0)(n => if (n > 0) n else 0x3fffffffffffffffL)
    private[this] val hPhase  = Handlers.InLAux   (this, shape.in1)()
    private[this] val hOut    = Handlers.OutIMain (this, shape.out)

    private[this] var phase     : Long  = _
    private[this] var remPeriod : Long = _

    private[this] var nextPeriod  = true

    protected def onDone(inlet: Inlet[_]): Unit = assert(false)

    @tailrec
    protected def process(): Unit = {
      if (nextPeriod) {
        if (!(hPeriod.hasNext && hPhase.hasNext)) return
        var period   = hPeriod .next()
        val phaseOff = hPhase  .next()
        if (period == 0L) {
          period = 0x3fffffffffffffffL // Long.MaxValue
        }
        phase       = (phaseOff + period - 1) % period + 1
        remPeriod   = period
        nextPeriod  = false
      }

      {
        val rem     = min(hOut.available, remPeriod).toInt
        if (rem == 0) return
        val out     = hOut.array
        var outOffI = hOut.offset
        val stop    = outOffI + rem

        val periodV = hPeriod.value
        var phaseV  = phase

        while (outOffI < stop ) {
          val v = if (phaseV >= periodV) {
            phaseV %= periodV
            1
          } else {
            0
          }
          out(outOffI) = v
          phaseV      += 1
          outOffI     += 1
        }
        hOut.advance(rem)
        phase      = phaseV
        remPeriod -= rem

        if (remPeriod == 0L) {
          nextPeriod = true
          process()
        }
      }
    }
  }
}