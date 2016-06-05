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

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object StreamIn {
  def single(peer: OutD): StreamIn = new Single(peer)

  def multi(peer: OutD, numSinks: Int): StreamIn = new Multi(peer, numSinks)

  object unused extends StreamIn {
    def toDouble(implicit b: Builder): OutD = throw new UnsupportedOperationException("StreamIn.unused.toDouble")
    def toInt   (implicit b: Builder): OutI = throw new UnsupportedOperationException("StreamIn.unused.toInt"   )
  }

  private final class Single(peer: OutD) extends StreamIn {
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
        ctrl.returnBufD(bufD)
        bufI
      }
    }
  }

  private final class Multi(peer: OutD, numSinks: Int) extends StreamIn {
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

    def toInt(implicit b: Builder): OutI = single(alloc()).toInt  // just reuse this functionality
  }
}
trait StreamIn {
  def toDouble(implicit b: Builder): OutD
  def toInt   (implicit b: Builder): OutI
}

object StreamOut {
  implicit def fromDouble   (peer:     OutD ):     StreamOut  = new StreamOut(peer) // Double(peer)
  implicit def fromDoubleVec(peer: Vec[OutD]): Vec[StreamOut] = peer.map(new StreamOut(_))

//  final case class Double(peer: OutD) extends StreamOut {
//    def toDouble(implicit b: Builder): OutD = peer
//    def toInt   (implicit b: Builder): OutI = ???
//  }
}
final class StreamOut(val peer: OutD) extends AnyVal