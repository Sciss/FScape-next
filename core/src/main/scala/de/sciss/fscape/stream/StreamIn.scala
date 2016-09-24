/*
 *  StreamIn.scala
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

import akka.stream.Outlet
import akka.stream.scaladsl.{GraphDSL, Sink}

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
    def toAny   (implicit b: Builder): Outlet[BufLike] = throw new UnsupportedOperationException("StreamIn.unused.toAny")
    def toDouble(implicit b: Builder): OutD = throw new UnsupportedOperationException("StreamIn.unused.toDouble")
    def toInt   (implicit b: Builder): OutI = throw new UnsupportedOperationException("StreamIn.unused.toInt"   )
    def toLong  (implicit b: Builder): OutL = throw new UnsupportedOperationException("StreamIn.unused.toLong"  )
    def toElem  (implicit b: Builder): Outlet[Elem] = throw new UnsupportedOperationException("StreamIn.unused.toElem")

    def isInt   : Boolean = false
    def isLong  : Boolean = false
    def isDouble: Boolean = true    // arbitrary
  }

  private trait DoubleLike extends StreamIn {
    final def isInt   : Boolean = false
    final def isLong  : Boolean = false
    final def isDouble: Boolean = true

    final def toAny(implicit b: Builder): Outlet[BufLike] = toDouble.as[BufLike]  // retarded Akka API. Why is Outlet not covariant?

    final type Elem = BufD

    final def toElem(implicit b: Builder): OutD = toDouble
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
      builder.map(peer) { bufD =>
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
      builder.map(peer) { bufD =>
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

  private trait IntLike extends StreamIn {
    final def isInt   : Boolean = true
    final def isLong  : Boolean = false
    final def isDouble: Boolean = false

    final def toAny(implicit b: Builder): Outlet[BufLike] = toInt.as[BufLike]

    final type Elem = BufI

    final def toElem(implicit b: Builder): OutI = toInt
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
      builder.map(peer) { bufI =>
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
      builder.map(peer) { bufI =>
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

  private trait LongLike extends StreamIn {
    final def isInt   : Boolean = false
    final def isLong  : Boolean = true
    final def isDouble: Boolean = false

    final def toAny(implicit b: Builder): Outlet[BufLike] = toLong.as[BufLike]

    final type Elem = BufL

    final def toElem(implicit b: Builder): OutL = toLong
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
      builder.map(peer) { bufL =>
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
      builder.map(peer) { bufL =>
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
      if (broad == null) broad = BroadcastBuf(peer, numSinks)
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
      if (broad == null) broad = BroadcastBuf(peer, numSinks)
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
      if (broad == null) broad = BroadcastBuf(peer, numSinks)
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
  def toDouble(implicit b: Builder): OutD
  def toInt   (implicit b: Builder): OutI
  def toLong  (implicit b: Builder): OutL
  def toAny   (implicit b: Builder): Outlet[BufLike]

  def isInt   : Boolean
  def isLong  : Boolean
  def isDouble: Boolean

  type Elem <: BufLike

  def toElem  (implicit b: Builder): Outlet[Elem]
}

object StreamOut {
  implicit def fromDouble   (peer:     OutD ):     StreamOut  = new StreamOutD(peer)
  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamOut] = peer.map(new StreamOutD(_))
  implicit def fromInt      (peer:     OutI ):     StreamOut  = new StreamOutI(peer)
  implicit def fromLong     (peer:     OutL ):     StreamOut  = new StreamOutL(peer)

  private final class StreamOutD(peer: OutD) extends StreamOut {
    override def toString = s"StreamOut($peer)"

    def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn = (numChildren: @switch) match {
      case 0 =>
        implicit val dsl = b.dsl
        import GraphDSL.Implicits._
        peer ~> Sink.ignore
        StreamIn.unused
      case 1 => StreamIn.singleD(peer)
      case n => StreamIn.multiD (peer, numChildren)
    }
  }

  private final class StreamOutI(peer: OutI) extends StreamOut {
    override def toString = s"StreamOut($peer)"

    def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn = (numChildren: @switch) match {
      case 0 =>
        implicit val dsl = b.dsl
        import GraphDSL.Implicits._
        peer ~> Sink.ignore
        StreamIn.unused
      case 1 => StreamIn.singleI(peer)
      case n => StreamIn.multiI (peer, numChildren)
    }
  }

  private final class StreamOutL(peer: OutL) extends StreamOut {
    override def toString = s"StreamOut($peer)"

    def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn = (numChildren: @switch) match {
      case 0 =>
        implicit val dsl = b.dsl
        import GraphDSL.Implicits._
        peer ~> Sink.ignore
        StreamIn.unused
      case 1 => StreamIn.singleL(peer)
      case n => StreamIn.multiL (peer, numChildren)
    }
  }
}
trait StreamOut {
  def toIn(numChildren: Int)(implicit b: stream.Builder): StreamIn
}