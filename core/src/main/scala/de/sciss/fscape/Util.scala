/*
 *  Util.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

object Util {
  final val Pi2: Double = math.Pi * 2

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

  /** Fills buffer with zeroes. */
  def clear(buf: Array[Double], off: Int, len: Int): Unit =
  fill(buf, off = off, len = len, value = 0.0)

  /** Fills buffer with zeroes. */
  def clear(buf: Array[Int], off: Int, len: Int): Unit =
    fill(buf, off = off, len = len, value = 0)

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
}