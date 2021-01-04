/*
 *  NormalizeWindow.scala
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
import de.sciss.fscape.graph.NormalizeWindow.{FitBipolar, FitUnipolar, ModeMax, Normalize, ZeroMean}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.annotation.switch

object NormalizeWindow {
  def apply(in: OutD, size: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(mode, stage.in2)

    stage.out
  }

  private final val name = "NormalizeWindow"

  private type Shp = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape3(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.size"  ),
      in2 = InI (s"$name.mode"  ),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends FilterWindowedInAOutA[Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize   = Handlers.InIAux(this, shape.in1)(math.max(1, _))
    private[this] val hMode   = Handlers.InIAux(this, shape.in2)(_.clip(0, ModeMax))

    protected def winBufSize: Int = hSize.value

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hMode.hasNext
      if (ok) {
        hSize .next()
        hMode .next()
      }
      ok
    }

//    override protected val fullLastWindow = false

    protected def processWindow(): Unit = {
      val n = winBufSize // writeToWinOff.toInt
//      if (n < winSize) {
//        Util.clear(winBuf, n, winSize - n)
//      }
      assert (n > 0)
      val win   = winBuf
      val mode  = hMode.value
      (mode: @switch) match {
        case Normalize    => processNormalize (win, n)
        case FitUnipolar  => processFitRange  (win, n, lo =  0.0, hi =  1.0)
        case FitBipolar   => processFitRange  (win, n, lo = -1.0, hi = +1.0)
        case ZeroMean     => processZeroMean  (win, n)
      }
    }

    private def processNormalize(b: Array[Double], n: Int): Unit = {
      var max = Double.NegativeInfinity
      var i   = 0
      while (i < n) {
        val x = math.abs(b(i))
        if (x > max) max = x
        i += 1
      }
      if (max > 0) {
        val mul = 1.0 / max
        i = 0
        while (i < n) {
          b(i) *= mul
          i += 1
        }
      }
    }

    private def processFitRange(b: Array[Double], n: Int, lo: Double, hi: Double): Unit = {
      var min = Double.PositiveInfinity
      var max = Double.NegativeInfinity
      var i   = 0
      while (i < n) {
        val x = b(i)
        if (x < min) min = x
        if (x > max) max = x
        i += 1
      }
      val add = -min
      val mul = if (min < max) (hi - lo) / (max - min) else 1.0
      i = 0
      while (i < n) {
        b(i) = ((b(i) + add) * mul) + lo
        i += 1
      }
    }

    private def processZeroMean(b: Array[Double], n: Int): Unit = {
      var sum = 0.0
      var i   = 0
      while (i < n) {
        val x = b(i)
        sum += x
        i += 1
      }
      val add = -sum / n
      i = 0
      while (i < n) {
        b(i) += add
        i += 1
      }
    }
  }
}