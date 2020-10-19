/*
 *  PeakCentroid2D.scala
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

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedMultiInOut
import de.sciss.fscape.stream.impl.shapes.In6Out3Shape
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers

import scala.math.{max, min}

object PeakCentroid2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh1: OutD, thresh2: OutD, radius: OutI)
           (implicit b: Builder): (OutD, OutD, OutD) = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in     , stage.in0)
    b.connect(width  , stage.in1)
    b.connect(height , stage.in2)
    b.connect(thresh1, stage.in3)
    b.connect(thresh2, stage.in4)
    b.connect(radius , stage.in5)
    (stage.out0, stage.out1, stage.out2)
  }

  private final val name = "PeakCentroid2D"

  private type Shp = In6Out3Shape[BufD, BufI, BufI, BufD, BufD, BufI, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = In6Out3Shape(
      in0  = InD (s"$name.in"        ),
      in1  = InI (s"$name.width"     ),
      in2  = InI (s"$name.height"    ),
      in3  = InD (s"$name.thresh1"   ),
      in4  = InD (s"$name.thresh2"   ),
      in5  = InI (s"$name.radius"    ),
      out0 = OutD(s"$name.translateX"),
      out1 = OutD(s"$name.translateY"),
      out2 = OutD(s"$name.peak"      )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedMultiInOut {

    override def toString = s"$name-L@${hashCode.toHexString}"

    // in: OutD, width: OutI, height: OutI, thresh1: OutD, thresh2: OutD, radius: OutI

    private[this] val hIn     : InDMain   = InDMain (this, shape.in0)
    private[this] val hWidth  : InIAux    = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hHeight : InIAux    = InIAux  (this, shape.in2)(max(1, _))
    private[this] val hThresh1: InDAux    = InDAux  (this, shape.in3)()
    private[this] val hThresh2: InDAux    = InDAux  (this, shape.in4)()
    private[this] val hRadius : InIAux    = InIAux  (this, shape.in5)(max(1, _))
    private[this] val hOut0   : OutDMain  = OutDMain(this, shape.out0)
    private[this] val hOut1   : OutDMain  = OutDMain(this, shape.out1)
    private[this] val hOut2   : OutDMain  = OutDMain(this, shape.out1)

    private[this] var width  : Int    = _
    private[this] var height : Int    = _
    private[this] var thresh1: Double = _
    private[this] var thresh2: Double = _
    private[this] var radius : Int    = _

    private[this] var winBuf      : Array[Double] = _
    private[this] var size        : Int = -1

    private[this] var translateX  : Double = _
    private[this] var translateY  : Double = _
    private[this] var peak        : Double = _

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def mainInAvailable: Int = hIn.available

    protected def outAvailable: Int = {
      var res = Int.MaxValue
      if (!hOut0.isDone) res = min(res, hOut0.available)
      if (!hOut1.isDone) res = min(res, hOut1.available)
      if (!hOut2.isDone) res = min(res, hOut2.available)
      res
    }

    protected def mainInDone: Boolean = hIn.isDone

    protected def isHotIn(inlet: Inlet[_]): Boolean = inlet == hIn.inlet

    protected def flushOut(): Boolean = {
      var res = true
      if (!hOut0.isDone) res &= hOut0.flush()
      if (!hOut1.isDone) res &= hOut1.flush()
      if (!hOut2.isDone) res &= hOut2.flush()
      res
    }

    protected def outDone: Boolean = hOut0.isDone && hOut1.isDone && hOut2.isDone

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hWidth  .hasNext &&
        hHeight .hasNext &&
        hThresh1.hasNext &&
        hThresh2.hasNext &&
        hRadius .hasNext

      if (ok) {
        val oldSize = size
        width   = hWidth  .next()
        height  = hHeight .next()
        thresh1 = hThresh1.next()
        thresh2 = hThresh2.next()
        radius  = hRadius .next()

        size = width * height
        if (size != oldSize) {
          winBuf = new Array[Double](size)
        }

        // println(s"width = $width, height = $height; thresh1 = $thresh1, thresh2 = $thresh2; radius = $radius")
      }
      ok
    }

    protected def readWinSize : Long = size
    protected def writeWinSize: Long = 1

    protected def readIntoWindow(chunk: Int): Unit = {
      hIn.nextN(winBuf, readOff.toInt, chunk)
    }

    // private var COUNT = 0

    protected def writeFromWindow(chunk: Int): Unit = {
      assert(writeOff == 0 && chunk == 1)
      if (!hOut0.isDone) hOut0.next(translateX )
      if (!hOut1.isDone) hOut1.next(translateY )
      if (!hOut2.isDone) hOut2.next(peak       )
//      COUNT += 1
//      println(f"elapsed = ${COUNT * 1024 / 44100.0}%1.2f sec - tx $translateX%1.2f; ty $translateY%1.2f, pk $peak%1.2f")
//      println(f"tx ${(translateX + 0.5).toInt}, pk = $peak%1.2f")
    }

    @inline
    private def pixel(x: Int, y: Int): Double = winBuf(y * width + x)

    protected def processWindow(): Unit = {
      if (readOff < size) {
        val writeOffI = readOff.toInt
        Util.clear(winBuf, writeOffI, size - writeOffI)
      }

      var i     = 0
      var max   = Double.NegativeInfinity
      while (i < size) {
        val q = winBuf(i)
        if (q > max) {
          max = q
        }
        i += 1
      }
//      println(f"max = $max%1.3f")

      // ---- first run ----

      val threshM1 = thresh1 * max
      var cx = 0.0
      var cy = 0.0
      var cs = 0.0

      val wh  = width/2
      val hh  = height/2
      var y = 0
      while (y < height) {
        var x = 0
        while (x < width) {
          //        val q = image.pixel(x, y)
          //        if (q > threshM) {
          val q = pixel(x, y) - threshM1
          if (q > 0.0) {
            cx += q * (if (x >= wh) x - width  else x)
            cy += q * (if (y >= hh) y - height else y)
            cs += q
          }
          x += 1
        }
        y += 1
      }

      cx /= cs
      cy /= cs

      // ---- second run ----
      import numbers.Implicits._
      val wm      = width - 1
      val hm      = height - 1
      val xMin    = ((cx + 0.5).toInt - radius).wrap(0, wm)
      val yMin    = ((cy + 0.5).toInt - radius).wrap(0, hm)
      val trDiam  = radius + radius + 1
      val xMax    = (xMin + trDiam).wrap(0, wm)
      val yMax    = (yMin + trDiam).wrap(0, hm)

//      println(f"peak run 1 - ($cx%1.2f, $cy%1.2f, $cs%1.2f)")

      val threshM2 = thresh2 * max
      cx = 0.0
      cy = 0.0
      cs = 0.0
      y  = yMin
      do {
        var x = xMin
        do {
          val q = pixel(x, y)
          if (q > threshM2) {
            // val q = image.pixel(x, y) - threshM2
            // if (q > 0.0) {
            cx += q * (if (x >= wh) x - width  else x)
            cy += q * (if (y >= hh) y - height else y)
            cs += q
          }
          x = (x + 1) % width
        } while (x != xMax)
        y = (y + 1) % height
      } while (y != yMax)

      if (cs > 0) {
        cx /= cs
        cy /= cs
      }

//      println(f"peak run 2 - ($cx%1.2f, $cy%1.2f, $cs%1.2f)")

//      val peak = Product(translateX = cx, translateY = cy, peak = cs)
//      peak

      translateX = cx // bufOut0.buf(0) = cx
      translateY = cx // bufOut1.buf(0) = cy
      peak       = cs // bufOut2.buf(0) = cs
    }
  }
}