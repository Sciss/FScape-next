/*
 *  Util.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import de.sciss.file._

object Util {
  final val log2 = math.log(2)

  // ---- multi-channel ----

  def copy(in: Array[Array[Float]], inOff: Int, out: Array[Array[Double]], outOff: Int, len: Int): Unit = {
    var ch = 0
    while (ch < in.length) {
      var i     = inOff
      val stop  = i + len
      var j     = outOff
      val a     = in(ch)
      val b     = out(ch)
      while (i < stop) {
        b(j) = a(i).toDouble
        i += 1
        j += 1
      }
      ch += 1
    }
  }

  def copy(in: Array[Array[Double]], inOff: Int, out: Array[Array[Float]], outOff: Int, len: Int): Unit = {
    var ch = 0
    while (ch < in.length) {
      var i     = inOff
      val stop  = i + len
      var j     = outOff
      val a     = in(ch)
      val b     = out(ch)
      while (i < stop) {
        b(j) = a(i).toFloat
        i += 1
        j += 1
      }
      ch += 1
    }
  }

  def fill(buf: Array[Array[Double]], off: Int, len: Int, value: Double): Unit = {
    var ch = 0
    while (ch < buf.length) {
      var i     = off
      val stop  = i + len
      val a     = buf(ch)
      while (i < stop) {
        a(i) = value
        i += 1
      }
      ch += 1
    }
  }

  // ---- single channel ----

  /** Copies input to output. */
  def copy(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
    System.arraycopy(in, inOff, out, outOff, len)

  /** Copies input to output. */
  def copy(in: Array[Int], inOff: Int, out: Array[Int], outOff: Int, len: Int): Unit =
    System.arraycopy(in, inOff, out, outOff, len)

  /** Fills buffer with zeroes. */
  def clear(buf: Array[Double], off: Int, len: Int): Unit =
  fill(buf, off = off, len = len, value = 0.0)

  /** Fills buffer with zeroes. */
  def clear(buf: Array[Int], off: Int, len: Int): Unit =
    fill(buf, off = off, len = len, value = 0)

  /** Fills buffer with a constant value. */
  def fill(buf: Array[Boolean], off: Int, len: Int, value: Boolean): Unit = {
    var i     = off
    val stop  = i + len
    while (i < stop) {
      buf(i) = value
      i += 1
    }
  }
  /** Fills buffer with a constant value. */
  def fill(buf: Array[Int], off: Int, len: Int, value: Int): Unit = {
    var i     = off
    val stop  = i + len
    while (i < stop) {
      buf(i) = value
      i += 1
    }
  }

  /** Fills buffer with a constant value. */
  def fill(buf: Array[Double], off: Int, len: Int, value: Double): Unit = {
    var i     = off
    val stop  = i + len
    while (i < stop) {
      buf(i) = value
      i += 1
    }
  }

  def reverse(buf: Array[Double], off: Int, len: Int): Unit = {
    var i = off
    var j = i + len - 1
    while (i < j) {
      val tmp = buf(i)
      buf(i) = buf(j)
      buf(j) = tmp
      i += 1
      j -= 1
    }
  }

  def reverse[A <: AnyRef](buf: Array[A], off: Int, len: Int): Unit = {
    var i = off
    var j = i + len - 1
    while (i < j) {
      val tmp = buf(i)
      buf(i) = buf(j)
      buf(j) = tmp
      i += 1
      j -= 1
    }
  }

  /** Multiplies buffer with a scalar. */
  def mul(buf: Array[Double], off: Int, len: Int, value: Double): Unit = {
    var i     = off
    val stop  = i + len
    while (i < stop) {
      buf(i) *= value
      i += 1
    }
  }

  /** Multiplies input with output and replaces output. */
  def mul(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
    var i     = inOff
    var j     = outOff
    val stop  = i + len
    while (i < stop) {
      out(j) *= in(i)
      i += 1
      j += 1
    }
  }

  /** Adds input to output. */
  def add(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
    var i     = inOff
    var j     = outOff
    val stop  = i + len
    while (i < stop) {
      out(j) += in(i)
      i += 1
      j += 1
    }
  }

  def max(in: Array[Double], off: Int, len: Int): Double = {
    var res   = Double.NegativeInfinity
    var i     = off
    val stop  = i + len
    while (i < stop) {
      val d = in(i)
      if (d > res) res = d
      i += 1
    }
    res
  }

  def min(in: Array[Double], off: Int, len: Int): Double = {
    var res   = Double.PositiveInfinity
    var i     = off
    val stop  = i + len
    while (i < stop) {
      val d = in(i)
      if (d < res) res = d
      i += 1
    }
    res
  }

  /** If the input contains placeholder `%`, returns it unchanged,
    * otherwise determines an integer number in the name and replaces
    * it by `%d`.
    */
  def mkTemplate(in: File): File = {
    val n = in.name
    if (n.contains("%")) in else {
      val j = n.lastIndexWhere(_.isDigit)
      if (j < 0) throw new IllegalArgumentException(
        s"Cannot make template out of file name '$n'. Must contain '%d' or integer number.")

      var i = j
      while (i > 0 && n.charAt(i - 1).isDigit) i -= 1
      val pre   = n.substring(0, i)
      val post  = n.substring(j + 1)
      val nn    = s"$pre%d$post"
      in.replaceName(nn)
    }
  }
}