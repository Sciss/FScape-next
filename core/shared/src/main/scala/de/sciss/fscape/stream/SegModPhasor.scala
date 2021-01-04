/*
 *  SegModPhasor.scala
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
import de.sciss.fscape.Log.{stream => logStream}

import scala.annotation.tailrec

object SegModPhasor {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "SegModPhasor"

  private type Shp = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hFreqN  = Handlers.InDMain  (this, shape.in0)
    private[this] val hPhase  = Handlers.InDAux   (this, shape.in1)(_ % 1.0)
    private[this] val hOut    = Handlers.OutDMain (this, shape.out)

    private[this] var incr    : Double = _  // single sample delay
    private[this] var phaseOff: Double = 0.0
    private[this] var phase   : Double = 0.0  // internal state; does not include `phaseOff`

    private[this] var nextPhase : Boolean = true

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    @tailrec
    def process(): Unit = {
      logStream.debug(s"process() $this")

      if (nextPhase) {
        if (!(hFreqN.hasNext && hPhase.hasNext)) {
          if (hFreqN.isDone && hOut.flush()) completeStage()
          return
        }
        incr      = hFreqN.next()
        phaseOff  = hPhase.next()
        if (incr == 0.0) {
          if (hOut.flush()) completeStage() // what else can we do?
          return
        }
        nextPhase = false
      }

      val rem = hOut.available
      if (rem == 0) return
      processChunk(rem)
      if (nextPhase) process()
    }

    private def processChunk(rem: Int): Unit = {
      val _incr       = incr
      val _phaseOff   = phaseOff
      var _phase      = phase
      val out         = hOut.array
      val outOff0     = hOut.offset
      var outOff      = outOff0
      var _nextPhase  = false
      val stop        = outOff + rem

      while (!_nextPhase && outOff < stop) {
        val x         = (_phase + _phaseOff) % 1.0
        out(outOff)  = _phase // x
        outOff += 1
        _phase        = (_phase + _incr) % 1.0
        val y         =  x      + _incr
        if (y >= 1.0) {
          _nextPhase = true
        }
      }
      nextPhase = _nextPhase
      phase     = _phase
      val chunk = outOff - outOff0
      hOut.advance(chunk)
    }
  }
}