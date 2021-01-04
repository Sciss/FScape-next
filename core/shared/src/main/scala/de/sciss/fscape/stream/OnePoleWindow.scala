/*
 *  OnePoleWindow.scala
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

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedInDOutD
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.math.{abs, max}

object OnePoleWindow {
  def apply(in: OutD, size: OutI, coef: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(coef, stage.in2)
    stage.out
  }

  private final val name = "OnePoleWindow"

  private type Shp = FanInShape3[BufD, BufI, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape3(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InD (s"$name.coef"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedInDOutD {

    protected     val hIn  : InDMain  = InDMain  (this, shape.in0)
    protected     val hOut : OutDMain = OutDMain (this, shape.out)
    private[this] val hSize: InIAux   = InIAux   (this, shape.in1)(max(1, _))
    private[this] val hCoef: InDAux   = InDAux   (this, shape.in2)()

    protected def winBufSize: Int = hSize.value

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hCoef.hasNext
      if (ok) {
        hSize.next()
        hCoef.next()
      }
      ok
    }

    override protected def readIntoWindow(chunk: Int): Unit = {
      val cy    = hCoef.value
      val cx    = 1.0 - abs(cy)
      val a     = hIn.array
      var ai    = hIn.offset
      val b     = winBuf
      var bi    = readOff.toInt
      val stop  = ai + chunk
      while (ai < stop) {
        val x0 = a(ai)
        val y0 = b(bi)
        val y1 = (cx * x0) + (cy * y0)
        b(bi)  = y1
        ai += 1
        bi += 1
      }
      hIn.advance(chunk)
    }

    protected def processWindow(): Unit = ()
  }
}