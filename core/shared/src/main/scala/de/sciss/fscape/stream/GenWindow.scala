/*
 *  GenWindow.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec

object GenWindow {
  import graph.GenWindow.{Hann, Shape => WinShape}

  def apply(size: OutL, shape: OutI, param: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0) 
    b.connect(size  , stage.in0)
    b.connect(shape , stage.in1)
    b.connect(param , stage.in2)
    stage.out
  }

  private final val name = "GenWindow"

  private type Shp = FanInShape3[BufL, BufI, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {

    val shape: Shape = new FanInShape3(
      in0 = InL (s"$name.size" ),
      in1 = InI (s"$name.shape"),
      in2 = InD (s"$name.param"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape)  {

    private[this] val hSize   = Handlers.InLAux   (this, shape.in0)(math.max(0L, _))
    private[this] val hShape  = Handlers.InIAux   (this, shape.in1)(_.clip(WinShape.MinId, WinShape.MaxId))
    private[this] val hParam  = Handlers.InDAux   (this, shape.in2)()
    private[this] val hOut    = Handlers.OutDMain (this, shape.out)

    private[this] var winSize: Long     = _
    private[this] var _shape : WinShape = Hann  // arbitrary default
    private[this] var param  : Double   = _
    private[this] var winOff : Long     = _

    protected def inputsEnded: Boolean = false         // never

    private[this] var nextWindow  = true

    protected def onDone(inlet: Inlet[_]): Unit = assert(false)

    @tailrec
    protected def process(): Unit = {
      while (nextWindow) {
        if (!(hSize.hasNext && hShape.hasNext && hParam.hasNext)) return
        winSize     = hSize .next()
        val shapeId = hShape.next()
        param       = hParam.next()
        if (shapeId != _shape.id) _shape = WinShape(shapeId)
        winOff      = 0L
        if (winSize > 0L) {
          nextWindow = false
        } else if (hSize.isConstant) {
          // XXX TODO --- should we signalize such abnormal termination?
          if (hOut.flush()) completeStage()
          return
        }
      }

      {
        val rem = math.min(hOut.available, winSize - winOff).toInt
        if (rem == 0) return
        _shape.fill(winSize = winSize, winOff = winOff, buf = hOut.array, bufOff = hOut.offset,
          len = rem, param = param)
        hOut.advance(rem)
        winOff += rem
        if (winOff == winSize) {
          nextWindow = true
          process()
        }
      }
    }
  }
}