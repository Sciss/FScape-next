/*
 *  SetResetFF.scala
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

import math.min
import scala.annotation.tailrec

object SetResetFF {
  def apply(set: OutI, reset: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(set   , stage.in0)
    b.connect(reset , stage.in1)
    stage.out
  }

  private final val name = "SetResetFF"

  private type Shp = FanInShape2[BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InI (s"$name.trig" ),
      in1 = InI (s"$name.reset"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hSet    = Handlers.InIMain  (this, shape.in0)
    private[this] val hReset  = Handlers.InIMain  (this, shape.in1)
    private[this] val hOut    = Handlers.OutIMain (this, shape.out)

    private[this] var highT   = false
    private[this] var highR   = false
    private[this] var state   = false

    protected def onDone(inlet: Inlet[_]): Unit = {
      checkDone()
      ()
    }

    private def checkDone(): Boolean = {
      val res = hSet.isDone && hReset.isDone
      if (res && hOut.flush()) completeStage()
      res
    }

    @tailrec
    protected def process(): Unit = {
      val remOut  = hOut.available
      if (remOut == 0) return

      val hasSet    = !hSet   .isDone
      val hasReset  = !hReset .isDone

      if (hasSet    && !hSet  .hasNext) return
      if (hasReset  && !hReset.hasNext) return

      var rem = remOut
      if (hasSet  ) rem = min(rem, hSet   .available)
      if (hasReset) rem = min(rem, hReset .available)

      val set       = if (hasSet  ) hSet  .array  else null
      val reset     = if (hasReset) hReset.array  else null
      var setOff    = if (hasSet  ) hSet  .offset else 0
      var resetOff  = if (hasReset) hReset.offset else 0

      val out     = hOut.array
      var outOff  = hOut.offset
      var h0t     = highT
      var h0r     = highR
      var h1t     = false
      var h1r     = false
      var s0      = state
      val stop    = setOff + rem
      while (setOff < stop) {
        h1t = hasSet    && set  (setOff   ) > 0
        h1r = hasReset  && reset(resetOff ) > 0
        if (h1t && !h0t) {
          s0 = true
        }
        if (h1r && !h0r) {
          s0 = false
        }
        out(outOff) = if (s0) 1 else 0
        setOff   += 1
        resetOff += 1
        outOff  += 1
        h0t      = h1t
        h0r      = h1r
      }
      hOut.advance(rem)
      if (hasSet  ) hSet  .advance(rem)
      if (hasReset) hReset.advance(rem)

      highT = h0t
      highR = h0r
      state = s0

      if (!checkDone()) process()
    }
  }
}