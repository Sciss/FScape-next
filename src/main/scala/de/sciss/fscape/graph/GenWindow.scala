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

package de.sciss.fscape
package graph

import de.sciss.fscape.stream.StreamIn

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object GenWindow {
  import Util.Pi2

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

    implicit def toGE(in: Shape): GE = in.id
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
}
final case class GenWindow(size: GE, shape: GE, param: GE = 0.0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = ???

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = ???

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamIn = ???
}