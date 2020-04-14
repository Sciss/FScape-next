/*
 *  StreamIn.scala
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

import java.util

import akka.stream.Outlet

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object StreamIn {
  def singleD(peer: OutD): StreamIn = new SingleD(peer)
  def singleI(peer: OutI): StreamIn = new SingleI(peer)
  def singleL(peer: OutL): StreamIn = new SingleL(peer)

  def multiD(peer: OutD, numSinks: Int): StreamIn = new MultiD(peer, numSinks)
  def multiI(peer: OutI, numSinks: Int): StreamIn = new MultiI(peer, numSinks)
  def multiL(peer: OutL, numSinks: Int): StreamIn = new MultiL(peer, numSinks)

  object unused extends StreamIn {
    private def unsupported(method: String): Nothing =
      throw new UnsupportedOperationException(s"StreamIn.unused.$method")

    def toAny   (implicit b: Builder): Outlet[BufLike]  = unsupported("toAny")
    def toDouble(implicit b: Builder): OutD             = unsupported("toDouble")
    def toInt   (implicit b: Builder): OutI             = unsupported("toInt")
    def toLong  (implicit b: Builder): OutL             = unsupported("toLong")
    def toElem  (implicit b: Builder): Outlet[Buf]      = unsupported("toElem")

    def isInt   : Boolean = false
    def isLong  : Boolean = false
    def isDouble: Boolean = true    // arbitrary

    // type Elem = Nothing

    def tpe: StreamType[A, Buf] = unsupported("tpe")
  }

  object DoubleType extends StreamType[Double, BufD] {
    val ordering: Ordering[Double] = implicitly[Ordering[Double]] // Ordering.Double

    final val zero      = 0.0
    final val minValue  = Double.NegativeInfinity
    final val maxValue  = Double.PositiveInfinity

    final def mkStreamOut(out: OutD): StreamOut = out

    def allocBuf()(implicit ctrl: Control): BufD = ctrl.borrowBufD()

    def newArray(size: Int): Array[Double] = new Array(size)

    def fill(a: Array[Double], off: Int, len: Int, elem: Double): Unit =
      util.Arrays.fill(a, off, off + len, elem)

    def clear(a: Array[Double], off: Int, len: Int): Unit =
      util.Arrays.fill(a, off, off + len, 0.0)

    def reverse(a: Array[Double], off: Int, len: Int): Unit = {
      var i = off
      var j = i + len - 1
      while (i < j) {
        val tmp = a(i)
        a(i) = a(j)
        a(j) = tmp
        i += 1
        j -= 1
      }
    }

    def isInt   : Boolean = false
    def isLong  : Boolean = false
    def isDouble: Boolean = true
  }

  trait DoubleLike extends StreamIn {
    final def isInt   : Boolean = false
    final def isLong  : Boolean = false
    final def isDouble: Boolean = true

    final def toAny (implicit b: Builder): Outlet[BufLike]  = toDouble.as[BufLike]  // retarded Akka API. Why is Outlet not covariant?
    final def toElem(implicit b: Builder): OutD             = toDouble

    final type A    = Double
    final type Buf  = BufD

    final def tpe: StreamType[A, Buf] = DoubleType
  }

  private final class SingleD(peer: OutD) extends DoubleLike {
    private[this] var exhausted = false

    def toDouble(implicit b: Builder): OutD = {
      require(!exhausted)
      exhausted = true
      peer
    }

    def toInt(implicit builder: Builder): OutI = {
      require(!exhausted)
      exhausted = true
      val ctrl = builder.control
      builder.map(peer, "D.toInt") { bufD =>
        val bufI = ctrl.borrowBufI()
        val sz = bufD.size
        bufI.size = sz
        val a = bufD.buf
        val b = bufI.buf
        var i = 0
        while (i < sz) {
          val x = a(i)
          b(i)  = math.max(Int.MinValue, math.min(Int.MaxValue, math.round(x))).toInt
          i += 1
        }
        bufD.release()
        bufI
      }
    }

    def toLong(implicit builder: Builder): OutL = {
      require(!exhausted)
      exhausted = true
      val ctrl = builder.control
      builder.map(peer, "D.toLong") { bufD =>
        val bufL = ctrl.borrowBufL()
        val sz = bufD.size
        bufL.size = sz
        val a = bufD.buf
        val b = bufL.buf
        var i = 0
        while (i < sz) {
          val x = a(i)
          b(i) = math.round(x)
          i += 1
        }
        bufD.release()
        bufL
      }
    }
  }

  object IntType extends StreamType[Int, BufI] {
    val ordering: Ordering[Int] = Ordering.Int

    final val zero      = 0
    final val minValue  = Int.MinValue
    final val maxValue  = Int.MaxValue

    final def mkStreamOut(out: OutI): StreamOut = out

    def allocBuf()(implicit ctrl: Control): BufI = ctrl.borrowBufI()

    def newArray(size: Int): Array[Int] = new Array(size)

    def fill(a: Array[Int], off: Int, len: Int, elem: Int): Unit =
      util.Arrays.fill(a, off, off + len, elem)

    def clear(a: Array[Int], off: Int, len: Int): Unit =
      util.Arrays.fill(a, off, off + len, 0)

    def reverse(a: Array[Int], off: Int, len: Int): Unit = {
      var i = off
      var j = i + len - 1
      while (i < j) {
        val tmp = a(i)
        a(i) = a(j)
        a(j) = tmp
        i += 1
        j -= 1
      }
    }

    def isInt   : Boolean = true
    def isLong  : Boolean = false
    def isDouble: Boolean = false
  }

  trait IntLike extends StreamIn {
    final def isInt   : Boolean = true
    final def isLong  : Boolean = false
    final def isDouble: Boolean = false

    final def toAny (implicit b: Builder): Outlet[BufLike]  = toInt.as[BufLike]
    final def toElem(implicit b: Builder): OutI             = toInt

    final type A    = Int
    final type Buf  = BufI

    final def tpe: StreamType[Int, BufI] = IntType
  }

  private final class SingleI(peer: OutI) extends IntLike {
    private[this] var exhausted = false

    def toInt(implicit b: Builder): OutI = {
      require(!exhausted)
      exhausted = true
      peer
    }

    def toLong(implicit builder: Builder): OutL = {
      require(!exhausted)
      exhausted = true
      val ctrl = builder.control
      builder.map(peer, "I.toLong") { bufI =>
        val bufL = ctrl.borrowBufL()
        val sz = bufI.size
        bufL.size = sz
        val a = bufI.buf
        val b = bufL.buf
        var i = 0
        while (i < sz) {
          val x = a(i)
          b(i)  = x.toLong
          i += 1
        }
        bufI.release()
        bufL
      }
    }

    def toDouble(implicit builder: Builder): OutD = {
      require(!exhausted)
      exhausted = true
      val ctrl = builder.control
      builder.map(peer, "I.toDouble") { bufI =>
        val bufD = ctrl.borrowBufD()
        val sz = bufI.size
        bufD.size = sz
        val a = bufI.buf
        val b = bufD.buf
        var i = 0
        while (i < sz) {
          val x = a(i)
          b(i) = x.toDouble
          i += 1
        }
        bufI.release()
        bufD
      }
    }
  }

  object LongType extends StreamType[Long, BufL] {
    val ordering: Ordering[Long] = Ordering.Long

    final val zero      = 0L
    final val minValue  = Long.MinValue
    final val maxValue  = Long.MaxValue

    final def mkStreamOut(out: OutL): StreamOut = out

    def allocBuf()(implicit ctrl: Control): BufL = ctrl.borrowBufL()

    def newArray(size: Int): Array[Long] = new Array(size)

    def fill(a: Array[Long], off: Int, len: Int, elem: Long): Unit =
      util.Arrays.fill(a, off, off + len, elem)

    def clear(a: Array[Long], off: Int, len: Int): Unit =
      util.Arrays.fill(a, off, off + len, 0L)

    def reverse(a: Array[Long], off: Int, len: Int): Unit = {
      var i = off
      var j = i + len - 1
      while (i < j) {
        val tmp = a(i)
        a(i) = a(j)
        a(j) = tmp
        i += 1
        j -= 1
      }
    }

    def isInt   : Boolean = false
    def isLong  : Boolean = true
    def isDouble: Boolean = false
  }

  trait LongLike extends StreamIn {
    final def isInt   : Boolean = false
    final def isLong  : Boolean = true
    final def isDouble: Boolean = false

    final def toAny (implicit b: Builder): Outlet[BufLike]  = toLong.as[BufLike]
    final def toElem(implicit b: Builder): OutL             = toLong

    final type A    = Long
    final type Buf  = BufL

    final def tpe: StreamType[Long, BufL] = LongType
  }

  private final class SingleL(peer: OutL) extends LongLike {
    private[this] var exhausted = false

    def toLong(implicit b: Builder): OutL = {
      require(!exhausted)
      exhausted = true
      peer
    }

    def toInt(implicit builder: Builder): OutI = {
      require(!exhausted)
      exhausted = true
      val ctrl = builder.control
      builder.map(peer, "L.toInt") { bufL =>
        val bufI = ctrl.borrowBufI()
        val sz = bufL.size
        bufI.size = sz
        val a = bufL.buf
        val b = bufI.buf
        var i = 0
        while (i < sz) {
          val x = a(i)
          b(i)  = math.max(Int.MinValue, math.min(Int.MaxValue, x)).toInt
          i += 1
        }
        bufL.release()
        bufI
      }
    }

    def toDouble(implicit builder: Builder): OutD = {
      require(!exhausted)
      exhausted = true
      val ctrl = builder.control
      builder.map(peer, "L.toDouble") { bufL =>
        val bufD = ctrl.borrowBufD()
        val sz = bufL.size
        bufD.size = sz
        val a = bufL.buf
        val b = bufD.buf
        var i = 0
        while (i < sz) {
          val x = a(i)
          b(i) = x.toDouble
          i += 1
        }
        bufL.release()
        bufD
      }
    }
  }

  private final class MultiD(peer: OutD, numSinks: Int) extends DoubleLike {
    private[this] var remain = numSinks
    private[this] var broad: Vec[OutD] = _ // create lazily because we need stream.Builder

    private def alloc()(implicit b: Builder): OutD = {
      require(remain > 0)
      if (broad == null) broad = Broadcast(peer, numSinks)
      remain -= 1
      val head +: tail = broad
      broad = tail
      head
    }

    def toDouble(implicit b: Builder): OutD = alloc()
    def toInt   (implicit b: Builder): OutI = singleD(alloc()).toInt   // just reuse this functionality
    def toLong  (implicit b: Builder): OutL = singleD(alloc()).toLong  // just reuse this functionality
  }

  private final class MultiI(peer: OutI, numSinks: Int) extends IntLike {
    private[this] var remain = numSinks
    private[this] var broad: Vec[OutI] = _ // create lazily because we need stream.Builder

    private def alloc()(implicit b: Builder): OutI = {
      require(remain > 0)
      if (broad == null) broad = Broadcast(peer, numSinks)
      remain -= 1
      val head +: tail = broad
      broad = tail
      head
    }

    def toDouble(implicit b: Builder): OutD = singleI(alloc()).toDouble   // just reuse this functionality
    def toInt   (implicit b: Builder): OutI = alloc()
    def toLong  (implicit b: Builder): OutL = singleI(alloc()).toLong     // just reuse this functionality
  }

  private final class MultiL(peer: OutL, numSinks: Int) extends LongLike {
    private[this] var remain = numSinks
    private[this] var broad: Vec[OutL] = _ // create lazily because we need stream.Builder

    private def alloc()(implicit b: Builder): OutL = {
      require(remain > 0)
      if (broad == null) broad = Broadcast(peer, numSinks)
      remain -= 1
      val head +: tail = broad
      broad = tail
      head
    }

    def toDouble(implicit b: Builder): OutD = singleL(alloc()).toDouble  // just reuse this functionality
    def toInt   (implicit b: Builder): OutI = singleL(alloc()).toInt     // just reuse this functionality
    def toLong  (implicit b: Builder): OutL = alloc()
  }
}
trait StreamIn {
  type A
  type Buf >: Null <: BufElem[A]

  def isInt   : Boolean
  def isLong  : Boolean
  def isDouble: Boolean

  def toInt   (implicit b: Builder): OutI
  def toLong  (implicit b: Builder): OutL
  def toDouble(implicit b: Builder): OutD
  def toAny   (implicit b: Builder): Outlet[BufLike]
  def toElem  (implicit b: Builder): Outlet[Buf]

  implicit def tpe: StreamType[A, Buf]
}

object StreamType {
  implicit def int   : StreamType[Int   , BufI] = StreamIn.IntType
  implicit def double: StreamType[Double, BufD] = StreamIn.DoubleType
  implicit def long  : StreamType[Long  , BufL] = StreamIn.LongType
}
trait StreamType[@specialized(Args) A, Buf <: BufElem[A]] {
  implicit val ordering: Ordering[A]

  def mkStreamOut(out: Outlet[Buf]): StreamOut

  def allocBuf()(implicit ctrl: Control): Buf

  def fill(a: Array[A], off: Int, len: Int, elem: A): Unit

  def clear(a: Array[A], off: Int, len: Int): Unit

  def reverse(a: Array[A], off: Int, len: Int): Unit

  def zero: A

  def minValue: A

  def maxValue: A

//  def reverse(a: Array[A], off: Int, len: Int): Unit

  def newArray(size: Int): Array[A]

  def isInt   : Boolean
  def isLong  : Boolean
  def isDouble: Boolean
}

object StreamOut {
  implicit def fromDouble   (peer:     OutD ):     StreamOut  = new StreamOutD(peer)
  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamOut] = peer.map(new StreamOutD(_))
  implicit def fromInt      (peer:     OutI ):     StreamOut  = new StreamOutI(peer)
  implicit def fromIntVec   (peer: Vec[OutI]): Vec[StreamOut] = peer.map(new StreamOutI(_))
  implicit def fromLong     (peer:     OutL ):     StreamOut  = new StreamOutL(peer)
  implicit def fromLongVec  (peer: Vec[OutL]): Vec[StreamOut] = peer.map(new StreamOutL(_))

  private final class StreamOutD(peer: OutD) extends StreamOut {
    override def toString = s"StreamOut($peer)"

    def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn = (numChildren: @switch) match {
      case 0 =>
        SinkIgnore(peer)
        StreamIn.unused
      case 1 => StreamIn.singleD(peer)
      case _ => StreamIn.multiD (peer, numChildren)
    }
  }

  private final class StreamOutI(peer: OutI) extends StreamOut {
    override def toString = s"StreamOut($peer)"

    def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn = (numChildren: @switch) match {
      case 0 =>
        SinkIgnore(peer)
        StreamIn.unused
      case 1 => StreamIn.singleI(peer)
      case _ => StreamIn.multiI (peer, numChildren)
    }
  }

  private final class StreamOutL(peer: OutL) extends StreamOut {
    override def toString = s"StreamOut($peer)"

    def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn = (numChildren: @switch) match {
      case 0 =>
        SinkIgnore(peer)
        StreamIn.unused
      case 1 => StreamIn.singleL(peer)
      case _ => StreamIn.multiL (peer, numChildren)
    }
  }
}
trait StreamOut {
  def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn
}