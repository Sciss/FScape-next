/*
 *  Biquad.scala
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

import akka.stream.{Attributes, FanInShape6, Inlet}
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import math.min
import scala.annotation.tailrec

object Biquad {
  def apply(in: OutD, b0: OutD, b1: OutD, b2: OutD, a1: OutD, a2: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(b0, stage.in1)
    b.connect(b1, stage.in2)
    b.connect(b2, stage.in3)
    b.connect(a1, stage.in4)
    b.connect(a2, stage.in5)
    stage.out
  }

  private final val name = "Biquad"

  private type Shp = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape6(
      in0 = InD (s"$name.in"  ),
      in1 = InD (s"$name.b0"  ),
      in2 = InD (s"$name.b1"  ),
      in3 = InD (s"$name.b2"  ),
      in4 = InD (s"$name.a1"  ),
      in5 = InD (s"$name.a2"  ),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {
    
    private[this] val hIn  = InDMain  (this, shape.in0)
    private[this] val hB0  = InDAux   (this, shape.in1)()
    private[this] val hB1  = InDAux   (this, shape.in2)()
    private[this] val hB2  = InDAux   (this, shape.in3)()
    private[this] val hA1  = InDAux   (this, shape.in4)()
    private[this] val hA2  = InDAux   (this, shape.in5)()
    private[this] val hOut = OutDMain (this, shape.out)
    
    private[this] var x1 = 0.0
    private[this] var x2 = 0.0
    private[this] var y1 = 0.0
    private[this] var y2 = 0.0

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    @tailrec
    protected def process(): Unit = {
      val remIO = min(hIn.available, hOut.available)
      if (remIO == 0) return
      val remB0 = hB0.available
      if (remB0 == 0) return
      val remB1 = hB1.available
      if (remB1 == 0) return
      val remB2 = hB2.available
      if (remB2 == 0) return
      val remA1 = hA1.available
      if (remA1 == 0) return
      val remA2 = hA2.available
      if (remA2 == 0) return
      
      val rem = min(remIO, min(remB0, min(remB1, min(remB2, min(remA1, remA2)))))
      
      var _x1 = x1
      var _x2 = x2
      var _y1 = y1
      var _y2 = y2

      var i = 0
      while (i < rem) {
        val x0 = hIn.next()
        val b0 = hB0.next()
        val b1 = hB1.next()
        val b2 = hB2.next()
        val a1 = hA1.next()
        val a2 = hA2.next()
        val y0 = b0 * x0 + b1 * _x1 + b2 * _x2 - a1 * _y1 - a2 * _y2
        hOut.next(y0)
        _y2 = _y1
        _y1 = y0
        _x2 = _x1
        _x1 = x0
        i += 1
      }
      x1 = _x1
      x2 = _x2
      y1 = _y1
      y2 = _y2

      if (hIn.isDone) {
        if (hOut.flush()) completeStage()
        return
      }

      process()
    }
  }
}