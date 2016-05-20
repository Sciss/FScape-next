/*
 *  Buf.scala
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

import java.util.concurrent.atomic.AtomicInteger

trait BufLike {
  def release()(implicit ctrl: Control): Unit
  def acquire()(implicit ctrl: Control): Unit

  /* @volatile */ var size: Int
}

object BufD {
  def apply(elems: Double*): BufD = {
    val arr = elems.toArray
    new BufD(arr, size = arr.length, borrowed = false)
  }

  def alloc(size: Int): BufD = {
    new BufD(new Array[Double](size), size = size, borrowed = true)
  }
}
final class BufD private(val buf: Array[Double], @volatile var size: Int, borrowed: Boolean)
  extends BufLike {

  private[this] val allocCount = new AtomicInteger(1)

  def acquire()(implicit ctrl: Control): Unit = if (borrowed)
    allocCount.getAndIncrement()

  def release()(implicit ctrl: Control): Unit = if (borrowed)
    if (allocCount.decrementAndGet() == 0) ctrl.returnBufD(this)

  override def toString = s"BufD(size = $size)"
}

object BufI {
  def apply(elems: Int*): BufI = {
    val arr = elems.toArray
    new BufI(arr, size = arr.length, borrowed = false)
  }

  def alloc(size: Int): BufI = {
    new BufI(new Array[Int](size), size = size, borrowed = true)
  }
}
final class BufI private(val buf: Array[Int], @volatile var size: Int, borrowed: Boolean) extends BufLike {
  private[this] val allocCount = new AtomicInteger(1)

  def acquire()(implicit ctrl: Control): Unit = if (borrowed)
    allocCount.getAndIncrement()

  def release()(implicit ctrl: Control): Unit = if (borrowed)
    if (allocCount.decrementAndGet() == 0) ctrl.returnBufI(this)

  override def toString = s"BufI(size = $size)"
}