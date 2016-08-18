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

import akka.stream.scaladsl.{GraphDSL, Sink}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object StreamIn {
  def singleD(peer: OutD): StreamIn = new SingleD(peer)
  def singleL(peer: OutL): StreamIn = new SingleL(peer)

  def multiD(peer: OutD, numSinks: Int): StreamIn = new MultiD(peer, numSinks)
  def multiL(peer: OutL, numSinks: Int): StreamIn = new MultiL(peer, numSinks)

  object unused extends StreamIn {
    def toDouble(implicit b: Builder): OutD = throw new UnsupportedOperationException("StreamIn.unused.toDouble")
    def toInt   (implicit b: Builder): OutI = throw new UnsupportedOperationException("StreamIn.unused.toInt"   )
    def toLong  (implicit b: Builder): OutL = throw new UnsupportedOperationException("StreamIn.unused.toLong"  )
  }

  private final class SingleD(peer: OutD) extends StreamIn {
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

  private final class SingleL(peer: OutL) extends StreamIn {
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

  private final class MultiD(peer: OutD, numSinks: Int) extends StreamIn {
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

  private final class MultiL(peer: OutL, numSinks: Int) extends StreamIn {
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
}

object StreamOut {
  implicit def fromDouble   (peer:     OutD ):     StreamOut  = new StreamOutD(peer) // Double(peer)
  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamOut] = peer.map(new StreamOutD(_))
  implicit def fromLong     (peer:     OutL ):     StreamOut  = new StreamOutL(peer) // Double(peer)

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