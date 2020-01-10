/*
 *  PriorityQueue.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{FilterIn3Impl, NodeImpl, StageImpl}

import scala.collection.mutable

/*

  TODO:
   - introduce type class that gives access to allocBuf

 */
object PriorityQueue {
  def apply[A, K >: Null <: BufElem[A],
            B, V >: Null <: BufElem[B]](keys: Outlet[K],
                                        values: Outlet[V], size: OutI)
                                       (implicit b: Builder, keyTpe: StreamType[A, K],
                                        valueTpe: StreamType[B, V]): Outlet[V] = {
    val stage0  = new Stage[A, K, B, V](b.layer)
    val stage   = b.add(stage0)
    b.connect(keys  , stage.in0)
    b.connect(values, stage.in1)
    b.connect(size  , stage.in2)
    stage.out
  }

  private final val name = "PriorityQueue"

  private type Shape[A, K >: Null <: BufElem[A], B, V >: Null <: BufElem[B]] =
    FanInShape3[K, V, BufI, V]

  private final class Stage[A, K >: Null <: BufElem[A],
                            B, V >: Null <: BufElem[B]](layer: Layer)
                                                       (implicit ctrl: Control,
                                                        keyTpe: StreamType[A, K], valueTpe: StreamType[B, V])
    extends StageImpl[Shape[A, K, B, V]](name) {

    val shape = new FanInShape3(
      in0 = Inlet[K] (s"$name.keys"  ),
      in1 = Inlet[V] (s"$name.values"),
      in2 = InI      (s"$name.size"  ),
      out = Outlet[V](s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic[A, K, B, V](shape, layer)
  }

  private final class Logic[A, K >: Null <: BufElem[A],
                            B, V >: Null <: BufElem[B]](shape: Shape[A, K, B, V], layer: Layer)
                                                       (implicit ctrl: Control, keyTpe: StreamType[A, K],
                                                        valueTpe: StreamType[B, V])
    extends NodeImpl(name, layer, shape)
      with FilterIn3Impl[K, V, BufI, V] {

    import keyTpe.{ordering => keyOrd}

    private[this] var size : Int  = _

    private[this] var outOff      = 0
    private[this] var outRemain   = 0
    private[this] var outSent     = true

    private[this] var bufRemain : Int = _

    private[this] var writeMode = false

    private[this] var queue : mutable.PriorityQueue[(A, B)] = _
    private[this] var result: Array[B] = _

    private[this] var value: B = _

    protected def allocOutBuf0(): V = valueTpe.allocBuf()

    // highest priority = lowest keys
    private[this] object SortedKeys extends Ordering[(A, B)] {
      def compare(x: (A, B), y: (A, B)): Int = keyOrd.compare(y._1, x._1)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      queue   = null
      result  = null
    }

    def process(): Unit = {
      logStream(s"process() $this ${if (writeMode) "W" else "R"}")

      if (writeMode) tryWrite()
      else {
        if (canRead) {
          readIns()
          if (queue == null) {
            size  = math.max(1, bufIn2.buf(0))
            queue = mutable.PriorityQueue.empty[(A, B)](SortedKeys)
          }
          copyInputToBuffer()
        }
        if (isClosed(in0) && !isAvailable(in0)) {
          val q       = queue
          queue       = null
          val sz      = q.size
          val _res    = valueTpe.newArray(sz)
          var i = 0
          while (i < sz) {
            _res(i)   = q.dequeue()._2
            i += 1
          }
          result      = _res
          bufRemain   = sz
          writeMode   = true
          tryWrite()
        }
      }
    }

    private def copyInputToBuffer(): Unit = {
      val _bufIn0   = bufIn0
      val _bufIn1   = bufIn1
      val _keys     = _bufIn0.buf
      val inRemain  = _bufIn0.size
      val _values   = if (_bufIn1 != null) _bufIn1.buf  else null
      val valStop   = if (_bufIn1 != null) _bufIn1.size else 0
      var _value    = value
      val q         = queue
      val chunk0    = math.min(size - q.size, inRemain)
      if (chunk0 > 0) { // queue not full yet, simply add items
        var i = 0
        while (i < chunk0) {
          if (i < valStop) _value = _values(i)
          val _key = _keys(i)
          q += _key -> _value
          i += 1
        }
      }
      if (chunk0 < inRemain) {  // queue is full, replace items
        var min = q.head._1
        var i = chunk0
        while (i < inRemain) {
          if (i < valStop) _value = _values(i)
          val _key = _keys(i)
          // if the key is higher than the lowest in the queue...
          if (keyOrd.compare(_key, min) > 0) {
            // ...we remove the head of the queue and add the new key
            q.dequeue()
            q += _key -> _value
            min = q.head._1
          }
          i += 1
        }
      }
      value = _value

//      bufWritten += inRemain
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
        val arr     = bufOut0.buf
        var outOffI = outOff
        val q       = result
        var _bufRem = bufRemain
        var i       = 0
        while (i < chunk) {
          _bufRem     -= 1
          val _value   = q(_bufRem)
          arr(outOffI) = _value
          outOffI     += 1
          i           += 1
        }
        outOff     = outOffI
        outRemain -= chunk
        bufRemain  = _bufRem
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