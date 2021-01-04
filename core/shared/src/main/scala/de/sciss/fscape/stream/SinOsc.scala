/*
 *  SinOsc.scala
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
import de.sciss.numbers.TwoPi

import scala.annotation.tailrec
import scala.math.min

object SinOsc {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "SinOsc"

  private type Shp = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO -- detect constant freq input and use multiplication instead of frame-by-frame addition for phase
  // (cf. Resample)
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hFreq   = Handlers.InDAux   (this, shape.in0)(_ * TwoPi)
    private[this] val hPhase  = Handlers.InDAux   (this, shape.in1)(_ % TwoPi)
    private[this] val hOut    = Handlers.OutDMain (this, shape.out)

    private[this] var phase   : Double = _  // internal state; does not include `phaseOff`

    protected def onDone(inlet: Inlet[_]): Unit = assert(false)

    @tailrec
    protected def process(): Unit = {
      val rem = min(hFreq.available, min(hPhase.available, hOut.available))
      if (rem == 0) return

      val out       = hOut.array
      var outOffI   = hOut.offset
      var phaseV    = phase
      val stop      = outOffI + rem

      while (outOffI < stop) {
        val incV      = hFreq .next()
        val phaseOffV = hPhase.next()
        val phaseVNew = (phaseV + incV) % TwoPi
        val x         = phaseV + phaseOffV
        out(outOffI)  = math.sin(x)
        phaseV        = phaseVNew
        outOffI += 1
      }
      hOut.advance(rem)
      phase = phaseV

      process()
    }
  }
}