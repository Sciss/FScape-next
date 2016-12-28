/*
 *  PeakCentroid2D.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.Attributes
import de.sciss.fscape.stream.impl.{FilterLogicImpl, In6Out3Impl, In6Out3Shape, StageImpl, NodeImpl, WindowedLogicImpl}
import de.sciss.numbers

object PeakCentroid2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh1: OutD, thresh2: OutD, radius: OutI)
           (implicit b: Builder): (OutD, OutD, OutD) = {
    val stage0  = new Stage
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

  private type Shape = In6Out3Shape[BufD, BufI, BufI, BufD, BufD, BufI, BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = In6Out3Shape(
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

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with In6Out3Impl[BufD, BufI, BufI, BufD, BufD, BufI, BufD, BufD, BufD] {

    override def toString = s"$name-L@${hashCode.toHexString}"

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()
    protected def allocOutBuf1(): BufD = ctrl.borrowBufD()
    protected def allocOutBuf2(): BufD = ctrl.borrowBufD()
    
    private[this] var width  : Int    = _
    private[this] var height : Int    = _
    private[this] var thresh1: Double = _
    private[this] var thresh2: Double = _
    private[this] var radius : Int    = _

    private[this] var winBuf      : Array[Double] = _
    private[this] var size        : Int = _

    private[this] var translateX  : Double = _
    private[this] var translateY  : Double = _
    private[this] var peak        : Double = _

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = size
      if (bufIn1 != null && inOff < bufIn1.size) {
        width = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        height = math.max(1, bufIn2.buf(inOff))
      }
      size = width * height
      if (size != oldSize) {
        winBuf = new Array[Double](size)
      }

      if (bufIn3 != null && inOff < bufIn3.size) thresh1 = bufIn3.buf(inOff)
      if (bufIn4 != null && inOff < bufIn4.size) thresh2 = bufIn4.buf(inOff)
      if (bufIn5 != null && inOff < bufIn5.size) radius  = math.max(1, bufIn5.buf(inOff))

//      println(s"width = $width, height = $height; thresh1 = $thresh1, thresh2 = $thresh2; radius = $radius")

      size
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    // private var COUNT = 0

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      assert(readFromWinOff == 0 && chunk == 1)
      // Util.copy(winBuf, readFromWinOff, bufOut0.buf, outOff, chunk)
      bufOut0.buf(outOff) = translateX
      bufOut1.buf(outOff) = translateY
      bufOut2.buf(outOff) = peak
//      COUNT += 1
//      println(f"elapsed = ${COUNT * 1024 / 44100.0}%1.2f sec - tx $translateX%1.2f; ty $translateY%1.2f, pk $peak%1.2f")
      println(f"tx ${(translateX + 0.5).toInt}, pk = $peak%1.2f")
    }

    @inline
    private def pixel(x: Int, y: Int): Double = winBuf(y * width + x)

    protected def processWindow(writeToWinOff: Long): Long = {
      if (writeToWinOff < size) {
        val writeOffI = writeToWinOff.toInt
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
      1
    }
  }
}