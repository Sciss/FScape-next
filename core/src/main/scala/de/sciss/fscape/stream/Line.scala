/*
 *  Line.scala
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
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InLAux, OutDMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{max, min}

object Line {
  def apply(start: OutD, end: OutD, length: OutL)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(start , stage.in0)
    b.connect(end   , stage.in1)
    b.connect(length, stage.in2)
    stage.out
  }

  private final val name = "Line"

  private type Shp = FanInShape3[BufD, BufD, BufL, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape3(
      in0 = InD (s"$name.start" ),
      in1 = InD (s"$name.end"   ),
      in2 = InL (s"$name.length"),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hStart: InDAux    = InDAux  (this, shape.in0)()
    private[this] val hEnd  : InDAux    = InDAux  (this, shape.in1)()
    private[this] val hLen  : InLAux    = InLAux  (this, shape.in2)(max(0L, _))
    private[this] val hOut  : OutDMain  = OutDMain(this, shape.out)

    private[this] var init = true

    private[this] var slope : Double  = _
    private[this] var frames: Long    = _

    protected def onDone(inlet: Inlet[_]): Unit = assert(false)

    @tailrec
    protected def process(): Unit = {
      if (init) {
        if (hLen.isConstant) {
          if (hOut.flush()) completeStage()
          return
        }
        if (!(hLen.hasNext && hStart.hasNext && hEnd.hasNext)) return
        val start = hStart.next()
        val end   = hEnd  .next()
        val len   = hLen  .next()
        slope   = if (len > 1) (end - start) / (len - 1) else 0.0
        frames  = 0L
        init    = false
      }

      {
        val _frames = frames
        val len     = hLen.value
        val rem     = min(hOut.available, len - _frames).toInt
        val stop    = _frames + rem
        val isLast  = stop == len

        if (rem > 0) {
          val _slope    = slope
          val _start    = hStart.value
          val cleanEnd  = isLast && len > 1
          val rem1      = if (cleanEnd) rem - 1 else rem
          var i = 0
          while (i < rem1) {
            val v = (_frames + i) * _slope + _start
            hOut.next(v)
            i += 1
          }
          frames = stop
          if (cleanEnd) {
            // replace last frame to match exactly the end value
            // to avoid problems with floating point noise
            hOut.next(hEnd.value)
          }
        }

        if (isLast) {
          init = true
          process()
        }
      }
    }
  }
}