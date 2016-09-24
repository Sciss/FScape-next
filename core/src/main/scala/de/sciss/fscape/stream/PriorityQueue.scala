/*
 *  PriorityQueue.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{FilterIn3Impl, NodeImpl, StageImpl}

import scala.collection.mutable

object PriorityQueue {
  def apply[V >: Null <: BufLike](keys: OutA, values: Outlet[V], size: OutI)(implicit b: Builder): Outlet[V] = {
    val stage0  = new Stage[V]
    val stage   = b.add(stage0)
    b.connect(keys  , stage.in0)
    b.connect(values, stage.in1)
    b.connect(size  , stage.in2)
    stage.out
  }

  private final val name = "PriorityQueue"

  private type Shape[V >: Null <: BufLike] = FanInShape3[BufLike, V, BufI, V]

  private final class Stage[V >: Null <: BufLike](implicit ctrl: Control) extends StageImpl[Shape[V]](name) {
    val shape = new FanInShape3(
      in0 = InA      (s"$name.keys"),
      in1 = Inlet[V] (s"$name.values"),
      in2 = InI      (s"$name.size"  ),
      out = Outlet[V](s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic[V >: Null <: BufLike](shape: Shape[V])(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with FilterIn3Impl[BufLike, V, BufI, V] {

    private[this] var size : Int  = _

    private[this] var outOff      = 0
    private[this] var outRemain   = 0
    private[this] var bufWritten  = 0
    private[this] var outSent     = true

    private[this] var bufOff    : Int = _
    private[this] var bufRemain : Int = _

    private[this] var writeMode = false

    private[this] var queue : mutable.PriorityQueue[Any]  = _
    private[this] var bufWin: Array[_]                    = _

    protected def allocOutBuf0(): V = ???

    override protected def stopped(): Unit = {
      super.stopped()
      queue   = null
      bufWin  = null
    }

    def process(): Unit = {
      logStream(s"process() $this ${if (writeMode) "W" else "R"}")

      if (writeMode) tryWrite()
      else {
        if (canRead) {
          readIns()
          if (queue == null) {
            size  = math.max(1, bufIn2.buf(0))
            queue = ??? // mutable.PriorityQueue.empty[A](bufIn0.ordering)
          }
          copyInputToBuffer()
        }
        if (isClosed(in0) && !isAvailable(in0)) {
          bufRemain   = math.min(bufWritten, size)
          bufOff      = (math.max(0L, bufWritten - size) % size).toInt
          writeMode   = true
          tryWrite()
        }
      }
    }

    private def copyInputToBuffer(): Unit = {
      val _bufIn0   = bufIn0
      val arr       = _bufIn0.buf
      val inRemain  = _bufIn0.size
      val q         = queue
      val chunk0    = math.min(size - q.size, inRemain)
      if (chunk0 > 0) { // queue not full yet, simply add items
        var i = 0
        while (i < chunk0) {
          q += arr(i)
          i += 1
        }
      }
      if (chunk0 < inRemain) {
        var min = q.head
        var i = chunk0
        while (i < inRemain) {
          ???
          i += 1
        }

      }

      ???

//      val chunk     = math.min(inRemain, len)
//      var inOff     = inRemain - chunk
//      var bufOff    = ((bufWritten + inOff) % len).toInt
//      val chunk1    = math.min(chunk, len - bufOff)
//      if (chunk1 > 0) {
//        // println(s"copy1($inOff / $inRemain -> $bufOff / $len -> $chunk1")
//        Util.copy(bufIn0.buf, inOff, bufWin, bufOff, chunk1)
//        bufOff = (bufOff + chunk1) % len
//        inOff += chunk1
//      }
//      val chunk2 = chunk - chunk1
//      if (chunk2 > 0) {
//        // println(s"copy2($inOff / $inRemain -> $bufOff / $len -> $chunk2")
//        Util.copy(bufIn0.buf, inOff, bufWin, bufOff, chunk2)
//        // bufOff = (bufOff + chunk2) % len
//        // inOff += chunk2
//      }
      bufWritten += inRemain
    }

    protected def tryWrite(): Unit = {
      if (outSent) {
        bufOut0        = allocOutBuf0()
        outRemain     = bufOut0.size
        outOff        = 0
        outSent       = false
      }

      val chunk = math.min(bufRemain, outRemain)
      if (chunk > 0) {
        ???
//        val chunk1  = math.min(len - bufOff, chunk)
//        Util.copy(bufWin, bufOff, bufOut0.buf, outOff, chunk1)
//        bufOff  = (bufOff + chunk1) % len
//        outOff += chunk1
//        val chunk2  = chunk - chunk1
//        if (chunk2 > 0) {
//          Util.copy(bufWin, bufOff, bufOut0.buf, outOff, chunk2)
//          bufOff  = (bufOff + chunk2) % len
//          outOff += chunk2
//        }
//
//        bufRemain -= chunk
//        outRemain -= chunk
      }

      val flushOut = bufRemain == 0
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(out0)) {
        if (outOff > 0) {
          bufOut0.size = outOff
          push(out0, bufOut0)
        } else {
          bufOut0.release()
        }
        bufOut0     = null
        outSent     = true
      }

      if (flushOut && outSent) {
        logStream(s"completeStage() $this")
        completeStage()
      }
    }
  }
}