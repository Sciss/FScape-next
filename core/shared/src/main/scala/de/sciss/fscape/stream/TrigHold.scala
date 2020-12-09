/*
 *  TrigHold.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{max, min}

object TrigHold {
  def apply(in: OutI, length: OutL, clear: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    b.connect(clear , stage.in2)
    stage.out
  }

  private final val name = "TrigHold"

  private type Shp = FanInShape3[BufI, BufL, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape3(
      in0 = InI (s"$name.in"    ),
      in1 = InL (s"$name.length"),
      in2 = InI (s"$name.clear" ),
      out = OutI(s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hIn     = Handlers.InIMain  (this, shape.in0)
    private[this] val hLength = Handlers.InLAux   (this, shape.in1)(max(_, 0L))
    private[this] val hClear  = Handlers.InIMain  (this, shape.in2)
    private[this] val hOut    = Handlers.OutIMain (this, shape.out)

    private[this] var highRemain = 0L

    protected def onDone(inlet: Inlet[_]): Unit = {
      if (inlet == hIn.inlet) {
        checkDone()
        ()
      }
      else process()  // hClear.inlet
    }

    private def checkDone(): Boolean = {
      val res = hIn.isDone
      if (res && hOut.flush()) completeStage()
      res
    }

    @tailrec
    protected def process(): Unit = {
      val remOut    = hOut.available
      if (remOut == 0) return

      val hasClear  = !hClear.isDone
      if (hasClear && !hClear.hasNext) return

      val remIn     = hIn.available
      val remClear  = if (hasClear) hClear.available else remIn // if closed, we treat a zero ("valid")
      val rem       = min(remOut, min(remIn, remClear))

      val in        = hIn.array
      val clear     = if (hasClear) hClear.array  else null
      var inOff     = hIn.offset
      var clearOff  = if (hasClear) hClear.offset else 0

      val out       = hOut.array
      var outOff    = hOut.offset
      var hiRem     = highRemain
      val stop      = inOff + rem
      var break     = false
      while (!break && inOff < stop) {
        val clearHi = hasClear && clear(clearOff) > 0
        if (clearHi) hiRem = 0
        val inHi = in(inOff) > 0
        break = inHi && !hLength.hasNext
        if (!break) {
          if (inHi) hiRem = hLength.next()
          val state = hiRem > 0
          out(outOff) = if (state) 1 else 0
          inOff    += 1
          clearOff += 1
          outOff   += 1
          if (hiRem > 0) hiRem -= 1
        }
      }
      val delta = inOff - hIn.offset
      val hasAdvanced = delta > 0
      if (hasAdvanced) {
        hOut.advance(delta)
        hIn .advance(delta)
        if (hasClear) hClear.advance(delta)

      }

      highRemain = hiRem

      if (!checkDone() && hasAdvanced) process()
    }
  }
}