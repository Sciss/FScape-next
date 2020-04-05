/*
 *  Pearson.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, FanInShape3, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import Handlers._
import de.sciss.fscape.{logStream => log}

import scala.annotation.tailrec

object Pearson {
  def apply(x: OutD, y: OutD, size: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(x   , stage.in0)
    b.connect(y   , stage.in1)
    b.connect(size, stage.in2)
    stage.out
  }

  private final val name = "Pearson"

  private type Shape = FanInShape3[BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control)
    extends StageImpl[Shape](name) {

    val shape: Shape = new FanInShape3(
      in0 = InD (s"$name.x"   ),
      in1 = InD (s"$name.y"   ),
      in2 = InI (s"$name.size"),
      out = OutD(s"$name.out" ),
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hX   : InDMain  = InDMain  (this, shape.in0)
    private[this] val hY   : InDMain  = InDMain  (this, shape.in1)
    private[this] val hSize: InIAux   = InIAux   (this, shape.in2)(math.max(1, _))
    private[this] val hOut : OutDMain = OutDMain (this, shape.out)

    private[this] var outValue  = 0.0
    private[this] var stage     = 0   // 0 -- read size, 1 -- read x and y, 2 -- write

    private[this] var size        = 0
    private[this] var xBuf    : Array[Double] = _
    private[this] var yBuf    : Array[Double] = _
    private[this] var xOff    = 0
    private[this] var xRem    = 0
    private[this] var yOff    = 0
    private[this] var yRem    = 0

    override protected def stopped(): Unit = {
      super.stopped()
      xBuf = null
      yBuf = null
    }

//    private var PUT = 0

    private def hotInsDone(): Boolean = {
//      println(s"hotInsDone(); stage = $stage, xRem = $xRem, yRem = $yRem, put $PUT")
      val res = hOut.flush()
      if (res) {
        completeStage()
      }
      res
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (inlet == shape.in0) {
        if (stage == 0 || (stage == 1 && xRem > 0)) hotInsDone()
      } else if (inlet == shape.in1) {
        if (stage == 0 || (stage == 1 && yRem > 0)) hotInsDone()
      }

    private def calc(): Double = {
      val _x  = xBuf
      val _y  = yBuf
      var xm  = 0.0
      var ym  = 0.0
      var i   = 0
      val len = size
      while (i < len) {
        xm += _x(i)
        ym += _y(i)
        i  += 1
      }
      xm /= len   // now xm = mean(x)
      ym /= len   // now ym = mean(y)

      var xsq = 0.0
      var ysq = 0.0
      var sum = 0.0
      i = 0
      while (i < len) {
        val xd = _x(i) - xm
        val yd = _y(i) - ym
        xsq += xd * xd
        ysq += yd * yd
        sum += xd * yd
        i  += 1
      }
      // now xsq = variance(x), ysq = variance(y)
      val denom = math.sqrt(xsq * ysq)
      val coef = if (denom > 0) sum / denom else sum
      coef
    }

    @tailrec
    protected def process(): Unit = {
      log(s"$this process()")

      //      if (hOutTour.isDone && hOutCost.isDone) {
      //        completeStage()
      //        return
      //      }

      if (stage == 0) { // read size
        if (!hSize.hasNext) return

        size = hSize.next()
        if (xBuf == null || xBuf.length != size) {
          xBuf = new Array(size)
          yBuf = new Array(size)
        }
        xOff  = 0
        xRem  = size
        yOff  = 0
        yRem  = size
        stage = 1

      } else if (stage == 1) {  // read init and weights
        while (stage == 1) {
          val numX = math.min(hX.available, xRem)
          val numY = math.min(hY.available, yRem)
          if (numX == 0 && numY == 0) return

          if (numX > 0) {
            hX.nextN(xBuf, xOff, numX)
            xOff += numX
            xRem -= numX
          }
          if (numY > 0) {
            hY.nextN(yBuf, yOff, numY)
            yOff += numY
            yRem -= numY
          }

          if (xRem == 0 && yRem == 0) {
            outValue  = calc()
            stage     = 2
          }
        }

      } else {  // write
        if (!hOut.hasNext) return

        hOut.next(outValue)
//        PUT += 1

        stage = 0
        if (hX.isDone || hY.isDone) {
          if (hotInsDone()) return
        }
      }

      process()
    }
  }
}
