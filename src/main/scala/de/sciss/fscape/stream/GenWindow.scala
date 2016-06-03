/*
 *  GenWindow.scala
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

package de.sciss.fscape.stream

import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape3}
import de.sciss.dsp.Util.Pi2
import de.sciss.fscape.stream.impl.{GenIn3Impl, WindowedLogicImpl}

import scala.annotation.switch

object GenWindow {
  def apply(size: OutI, shape: OutI, param: OutD)(implicit b: GBuilder, ctrl: Control): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    size  ~> stage.in0
    shape ~> stage.in1
    param ~> stage.in2
    stage.out
  }

  object Shape {
    def apply(id: Int): Shape = (id: @switch) match {
      case Hamming  .id => Hamming
      case Blackman .id => Blackman
      case Kaiser   .id => Kaiser
      case Rectangle.id => Rectangle
      case Hann     .id => Hann
      case Triangle .id => Triangle
    }

    final val MinId = Hamming.id
    final val MaxId = Hann   .id
  }

  sealed trait Shape {
    def id: Int
    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit
  }
  case object Hamming extends Shape {
    final val id = 0

    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = Pi2 / winSize
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        val d  = i * norm + math.Pi
        buf(j) = 0.54 + 0.46 * math.cos(d)
        i += 1
        j += 1
      }
    }
  }
  case object Blackman extends Shape {
    final val id = 1

    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = Pi2 / winSize
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        val d  = i * norm + math.Pi
        buf(j) = 0.42 + 0.5 * math.cos(d) + 0.08 * math.cos(2 * d)
        i += 1
        j += 1
      }
    }
  }
  case object Kaiser extends Shape {
    final val id = 2

    private[this] def calcBesselZero(x: Double): Double = {
      var d2  = 1.0
      var sum = 1.0
      var n   = 1
      val xh  = x * 0.5

      do {
        val d1 = xh / n
        n += 1
        d2 *= d1 * d1
        sum += d2
      } while (d2 >= sum * 1e-21) // precision is 20 decimal digits

      sum
    }

    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = 2.0 / winSize
      val iBeta = 1.0 / calcBesselZero(param)
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        val d  = i * norm - 1
        buf(j) = calcBesselZero(param * math.sqrt(1.0 - d * d)) * iBeta
        i += 1
        j += 1
      }
    }
  }
  case object Rectangle extends Shape {
    final val id = 3

    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      var j     = bufOff
      val stop  = j + len
      while (j < stop) {
        buf(j) = 1.0
        j += 1
      }
    }
  }
  case object Hann extends Shape {
    final val id = 4

    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = Pi2 / winSize
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        val d  = i * norm + math.Pi
        buf(j) = 0.5 + 0.5 * math.cos(d)
        i += 1
        j += 1
      }
    }
  }
  case object Triangle extends Shape {
    final val id = 5

    def fill(winSize: Int, winOff: Int, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = 2.0 / winSize
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        val d  = i * norm - 1
        buf(j) = 1.0 - math.abs(d)
        i += 1
        j += 1
      }
    }
  }
  // XXX TODO --- we should add some standard SuperCollider curve shapes like Welch

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape3[BufI, BufI, BufD, BufD]] {

    val shape = new FanInShape3(
      in0 = InI ("GenWindow.size" ),
      in1 = InI ("GenWindow.shape"),
      in2 = InD ("GenWindow.param"),
      out = OutD("GenWindow.out"  )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(protected val shape: FanInShape3[BufI, BufI, BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with WindowedLogicImpl[BufD, BufD, FanInShape3[BufI, BufI, BufD, BufD]]
      with GenIn3Impl                               [BufI, BufI, BufD, BufD] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    // private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int    = _
    private[this] var _shape : Shape  = Hann  // arbitrary default
    private[this] var param  : Double = _

    protected def shouldComplete(): Boolean = false         // never
    protected def inAvailable   (): Int     = ctrl.bufSize  // !

    protected def startNextWindow(inOff: Int): Int = {
//      val oldSize = winSize
      if (bufIn0 != null && inOff < bufIn0.size) {
        winSize = math.max(0, bufIn0.buf(inOff))
      }
      if (bufIn1 != null && inOff < bufIn1.size) {
        val shapeId = math.max(Shape.MinId, math.min(Shape.MaxId, bufIn1.buf(inOff)))
        if (shapeId != _shape.id) _shape = Shape(shapeId)
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        param = bufIn2.buf(inOff)
      }
//      if (winSize != oldSize) {
//        winBuf = new Array[Double](winSize)
//      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = ()

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
      // Util.copy(winBuf, readFromWinOff, bufOut.buf, outOff, chunk)
      _shape.fill(winSize = winSize, winOff = readFromWinOff, buf = bufOut.buf, bufOff = outOff,
        len = chunk, param = param)
    }

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = writeToWinOff
  }
}