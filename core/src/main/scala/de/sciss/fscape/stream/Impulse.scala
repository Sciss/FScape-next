/*
 *  Impulse.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet}
import de.sciss.fscape.stream.impl.Handlers.{InDMain, OutIMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers

import scala.annotation.tailrec
import scala.math.min

object Impulse {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "Impulse"

  private type Shp = FanInShape2[BufD, BufD, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO -- detect constant freq input and use multiplication instead of frame-by-frame addition for phase
  // (cf. Resample)
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] var incr    : Double = _
    private[this] var phaseOff: Double = _
    private[this] var phase   : Double = _    // internal state; does not include `phaseOff`
    private[this] var _init = true

    private[this] val hFreq   : InDMain   = InDMain (this, shape.in0)
    private[this] val hPhase  : InDMain   = InDMain (this, shape.in1)
    private[this] val hOut    : OutIMain  = OutIMain(this, shape.out)

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    @tailrec
    protected def process(): Unit = {
      val remOut    = hOut.available
      if (remOut == 0) return

      val hasFreq   = hFreq  .hasNext
      val hasPhase  = hPhase .hasNext
      val aFreq     = if (hasFreq ) hFreq .array  else null
      val aPhase    = if (hasPhase) hPhase.array  else null
      var offFreq   = if (hasFreq ) hFreq .offset else 0
      var offPhase  = if (hasPhase) hPhase.offset else 0
      val out       = hOut.array
      var offOut    = hOut.offset

      var incrV     = incr
      var phaseOffV = phaseOff
      var phaseV    = phase
      var y         = 0.0

      import numbers.Implicits._

      if (_init) {
        if (!(hasFreq && hasPhase)) return

        incrV     = aFreq (offFreq)
        phaseOffV = aPhase(offPhase).wrap(0.0, 1.0)
        phaseV    = -incrV
        y         = (phaseV + phaseOffV).wrap(0.0, 1.0)
        if (y == 0.0) y = 1.0
        _init     = false
      } else {
        y         = (phaseV + phaseOffV).wrap(0.0, 1.0)
      }

      val freqRem   = if (hasFreq ) hFreq .available else if (hFreq .isDone) Int.MaxValue else 0
      val phaseRem  = if (hasPhase) hPhase.available else if (hPhase.isDone) Int.MaxValue else 0
      val remIn     = min(freqRem, phaseRem)
      if (remIn == 0) return
      val chunk     = min(remIn, remOut)
      val stop      = offFreq + chunk

      while (offFreq < stop) {
        if (hasFreq ) incrV     = aFreq (offFreq)
        if (hasPhase) phaseOffV = aPhase(offPhase).wrap(0.0, 1.0)
        val phaseVNew = (phaseV    + incrV    )   .wrap(0.0, 1.0)
        val x         = (phaseVNew + phaseOffV)   .wrap(0.0, 1.0)
        val t         = x < y
        out(offOut)   = if (t) 1 else 0
        phaseV        = phaseVNew
        y             = x
        offFreq     += 1
        offPhase    += 1
        offOut      += 1
      }
      if (hasFreq ) hFreq .advance(chunk)
      if (hasPhase) hPhase.advance(chunk)
      hOut.advance(chunk)

      incr      = incrV
      phaseOff  = phaseOffV
      phase     = phaseV

      process()
    }
  }
}