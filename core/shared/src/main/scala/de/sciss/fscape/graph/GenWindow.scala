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
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.numbers.TwoPi

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object GenWindow {
  object Shape {
    def apply(id: Int): Shape = (id: @switch) match {
      case Hamming      .id => Hamming
      case Blackman     .id => Blackman
      case Kaiser       .id => Kaiser
      case Rectangle    .id => Rectangle
      case Hann         .id => Hann
      case Triangle     .id => Triangle
      case Gauss        .id => Gauss
      case Sinc         .id => Sinc
      case RaisedCosine .id => RaisedCosine
      case Line         .id => Line
    }

    final val MinId: Int = Hamming.id
    final val MaxId: Int = Line   .id

    implicit def toGE(in: Shape): GE = in.id
  }

  sealed trait Shape {
    def id: Int
    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit
  }

  sealed trait SimpleShape extends Shape {
    def apply(size: GE): GE = GenWindow(size, this)
  }

  sealed trait ParamShape extends Shape {
    def apply(size: GE, param: GE = 0.0): GE = GenWindow(size, this, param)
  }

  case object Hamming extends SimpleShape {
    final val id = 0

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = TwoPi / winSize
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

  case object Blackman extends SimpleShape {
    final val id = 1

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = TwoPi / winSize
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

  case object Kaiser extends ParamShape {
    final val id = 2

    private def calcBesselZero(x: Double): Double = {
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

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
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

    def mul(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = 2.0 / winSize
      val iBeta = 1.0 / calcBesselZero(param)
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        val d  = i * norm - 1
        buf(j) *= calcBesselZero(param * math.sqrt(1.0 - d * d)) * iBeta
        i += 1
        j += 1
      }
    }
  }

  case object Rectangle extends SimpleShape {
    final val id = 3

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      var j     = bufOff
      val stop  = j + len
      while (j < stop) {
        buf(j) = 1.0
        j += 1
      }
    }
  }

  case object Hann extends SimpleShape {
    final val id = 4

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = TwoPi / winSize
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

  case object Triangle extends SimpleShape {
    final val id = 5

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
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

  case object Gauss extends SimpleShape {
    final val id = 6

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val radius        = 0.5 * winSize
      val sigma         = radius/3
      val sigmaSqr2     = 2 * sigma * sigma
      // val sigmaPi2Sqrt  = math.sqrt(Pi2 * sigma)
      var i             = winOff
      val stop          = i + len
      var j             = bufOff
      while (i < stop) {
        val dist    = i - radius
        val distSqr = dist * dist
        buf(j)      = math.exp(-distSqr / sigmaSqr2) // / sigmaPi2Sqrt -- what the hell was this for?
        i          += 1
        j          += 1
      }
    }
  }

  /** The sinc or "cardinal sine" function.
    * The parameter is the normalized frequency (frequency divided by sampleRate).
    */
  case object Sinc extends ParamShape {
    final val id = 7

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val radius  = 0.5 * winSize
      val norm    = param * TwoPi
      var i       = winOff
      val stop    = i + len
      var j       = bufOff
      while (i < stop) {
        val d  = (i - radius) * norm
        buf(j) = if (d == 0.0) 1.0 else math.sin(d) / d
        i += 1
        j += 1
      }
    }
  }

  /** The "modulator" part for the roll-off in a raised cosine filter.
    * It follows the formula
    *
    * {{{
    *   x(n) = cos(a*n * Pi) / (1 - (2a*n)^2)
    * }}}
    *
    * with `a = freq/sr` the normalized roll-off frequency, specified as the window's
    * `param`, and the singularity of `x(0) = 1.0`.
    *
    */
  case object RaisedCosine extends ParamShape {
    final val id = 8

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val radius  = 0.5 * winSize
      val norm    = param * math.Pi // TwoPi
      var i       = winOff
      val stop    = i + len
      var j       = bufOff
      while (i < stop) {
        val d  = i - radius
        buf(j) = if (d == 0.0) 1.0 else {
          val d0 = d * norm
          val d1 = 4 * d0 * d0
          math.cos(d0 * math.Pi) / (1.0 - d1)
        }
        i += 1
        j += 1
      }
    }
  }

  /** Line begins at zero and moves linearly towards one, but not reaching it.
    * For example, a `GenWindow(10, GenWindow.Line) * 10` would count in integer
    * steps from `0` to `9`, then wrapping back to zero.
    */
  case object Line extends SimpleShape {
    final val id = 9

    def fill(winSize: Long, winOff: Long, buf: Array[Double], bufOff: Int, len: Int, param: Double): Unit = {
      val norm  = 1.0 / winSize
      var i     = winOff
      val stop  = i + len
      var j     = bufOff
      while (i < stop) {
        buf(j) = i * norm
        i += 1
        j += 1
      }
    }
  }

  // XXX TODO --- we should add some standard SuperCollider curve shapes like Welch
}

/** A repeated window generator UGen. It repeats the
  * same window again and again (unless parameters are modulated).
  * The parameters are demand-rate, polled once per window.
  *
  * @param size   the window size
  * @param shape  the identifier of the window shape, such as `GenWindow.Hann`.
  * @param param  parameter used by some window shapes, such as `GenWindow.Kaiser`
  */
final case class GenWindow(size: GE, shape: GE, param: GE = 0.0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(size.expand, shape.expand, param.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(size, shape, param) = args
    stream.GenWindow(size = size.toLong, shape = shape.toInt, param = param.toDouble)
  }
}