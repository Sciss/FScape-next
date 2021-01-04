/*
 *  Buf.scala
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

package de.sciss.fscape.stream

import java.util.concurrent.atomic.AtomicInteger

abstract class BufLike {
  type Elem

  def release()(implicit ctrl: Control): Unit
  def acquire(): Unit

  def assertAllocated(): Unit

  var size: Int

  def allocCount(): Int

  def buf: Array[Elem]
}

object BufD {
  def apply(elems: Double*): BufD = {
    val arr = elems.toArray
    new BufD(arr, borrowed = false)
  }

  def alloc(size: Int): BufD = {
    new BufD(new Array[Double](size), borrowed = true)
  }
}
final class BufD private(val buf: Array[Double], borrowed: Boolean)
  extends BufLike {

  type Elem = Double

  @volatile
  private[this] var _size = buf.length

  def size: Int = _size

  def size_=(value: Int): Unit = {
    assertOwned()
    _size = value
  }

  private[this] val _allocCount = if (borrowed) new AtomicInteger(1) else null

  def assertAllocated (): Unit = require(!borrowed || _allocCount.get() >  0)
  def assertOwned     (): Unit = require(!borrowed || _allocCount.get() == 1)

  def allocCount(): Int = _allocCount.get()

  def acquire(): Unit = if (borrowed) {
    val oldCount = _allocCount.getAndIncrement()
    if (oldCount == 0) _size = buf.length
    ()
  }

  def release()(implicit ctrl: Control): Unit = if (borrowed) {
    val newCount = _allocCount.decrementAndGet()
    require(newCount >= 0)
    if (newCount == 0) ctrl.returnBufD(this)
  }

  override def toString: String =
    if (size == 1) buf(0).toString else s"BufD(size = $size)@${hashCode.toHexString}"
}

object BufI {
  def apply(elems: Int*): BufI = {
    val arr = elems.toArray
    new BufI(arr, borrowed = false)
  }

  def alloc(size: Int): BufI = {
    new BufI(new Array[Int](size), borrowed = true)
  }
}
final class BufI private(val buf: Array[Int], borrowed: Boolean)
  extends BufLike {

  type Elem = Int

  @volatile
  private[this] var _size = buf.length

  def size: Int = _size

  def size_=(value: Int): Unit = {
    assertOwned()
    _size = value
  }

  private[this] val _allocCount = if (borrowed) new AtomicInteger(1) else null

  def assertAllocated (): Unit = require(!borrowed || _allocCount.get() >  0)
  def assertOwned     (): Unit = require(!borrowed || _allocCount.get() == 1)

  def allocCount(): Int = _allocCount.get()

  def acquire(): Unit = if (borrowed) {
    val oldCount = _allocCount.getAndIncrement()
    if (oldCount == 0) _size = buf.length
    ()
  }

  def release()(implicit ctrl: Control): Unit = if (borrowed) {
    val newCount = _allocCount.decrementAndGet()
    require(newCount >= 0)
    if (newCount == 0) ctrl.returnBufI(this)
  }

  override def toString: String =
    if (_size == 1) buf(0).toString else s"BufI(size = ${_size})@${hashCode.toHexString}"
}

object BufL {
  def apply(elems: Long*): BufL = {
    val arr = elems.toArray
    new BufL(arr, borrowed = false)
  }

  def alloc(size: Int): BufL = {
    new BufL(new Array[Long](size), borrowed = true)
  }
}
final class BufL private(val buf: Array[Long], borrowed: Boolean)
  extends BufLike {

  type Elem = Long

  @volatile
  private[this] var _size = buf.length

  def size: Int = _size

  def size_=(value: Int): Unit = {
    assertOwned()
    _size = value
  }

  private[this] val _allocCount = if (borrowed) new AtomicInteger(1) else null

  def assertAllocated(): Unit = require(!borrowed || _allocCount.get() > 0)
  def assertOwned    (): Unit = require(!borrowed || _allocCount.get() == 1)

  def allocCount(): Int = _allocCount.get()

  def acquire(): Unit = if (borrowed) {
    val oldCount = _allocCount.getAndIncrement()
    if (oldCount == 0) _size = buf.length
    ()
  }

  def release()(implicit ctrl: Control): Unit = if (borrowed) {
    val newCount = _allocCount.decrementAndGet()
    require(newCount >= 0)
    if (newCount == 0) ctrl.returnBufL(this)
  }

  override def toString: String =
    if (size == 1) buf(0).toString else s"BufL(size = $size)@${hashCode.toHexString}"
}