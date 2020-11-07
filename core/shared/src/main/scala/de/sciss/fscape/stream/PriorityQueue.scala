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
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.{max, min}

object PriorityQueue {
  def apply[A, K  <: BufElem[A], B, V  <: BufElem[B]](keys: Outlet[K], values: Outlet[V], size: OutI)
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

  private type Shp[K, V] = FanInShape3[K, V, BufI, V]

  private final class Stage[A, K <: BufElem[A], B, V <: BufElem[B]](layer: Layer)(implicit ctrl: Control,
                                                                                  keyTpe: StreamType[A, K],
                                                                                  valueTpe: StreamType[B, V])
    extends StageImpl[Shp[K, V]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet[K] (s"$name.keys"  ),
      in1 = Inlet[V] (s"$name.values"),
      in2 = InI      (s"$name.size"  ),
      out = Outlet[V](s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, K, B, V](shape, layer)
  }

  private final class Logic[A, K <: BufElem[A], B, V <: BufElem[B]](shape: Shp[K, V], layer: Layer)
                                                       (implicit ctrl: Control, keyTpe: StreamType[A, K],
                                                        valueTpe: StreamType[B, V])
    extends Handlers(name, layer, shape) {

    import keyTpe.{ordering => keyOrd}

    private[this] val hInK  = Handlers.InMain [A, K](this, shape.in0)
    private[this] val hInV  = Handlers.InMain [B, V](this, shape.in1)
    private[this] val hSize = Handlers.InIAux       (this, shape.in2)(max(0, _))
    private[this] val hOut  = Handlers.OutMain[B, V](this, shape.out)

    private[this] var len : Int  = _

    private[this] var bufRemain : Int = _

    private[this] var state = 0 // 0 = obtain len, 1 = read into queue, 2 = write from queue

    private[this] var queue : mutable.PriorityQueue[(A, B)] = _
    private[this] var result: Array[B] = _

    // highest priority = lowest keys
    private[this] object SortedKeys extends Ordering[(A, B)] {
      def compare(x: (A, B), y: (A, B)): Int = keyOrd.compare(y._1, x._1)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      queue   = null
      result  = null
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (state == 0) {
        completeStage()
      } else if (state == 1) {
        process()
      } // else ignore

    def process(): Unit = {
      logStream.debug(s"process() $this state = $state")

      if (state == 0) {
        if (!hSize.hasNext) return
        len       = hSize.next()
        queue     = mutable.PriorityQueue.empty[(A, B)](SortedKeys)
        state     = 1
      }
      if (state == 1) {
        readIntoQueue()
        if (!(hInK.isDone || hInV.isDone)) return
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
        state       = 2
      }

      writeFromQueue()
      if (bufRemain == 0) {
        if (hOut.flush()) completeStage()
      }
    }

    @tailrec
    private def readIntoQueue(): Unit = {
      val rem = min(hInK.available, hInV.available)
      if (rem == 0) return

      val keys      = hInK.array
      val values    = hInV.array
      val q         = queue
      val chunk0    = min(len - q.size, rem)
      if (chunk0 > 0) { // queue not full yet, simply add items
        var i = 0
        while (i < chunk0) {
          val value = values(i)
          val key   = keys  (i)
          q += key -> value
          i += 1
        }
      }
      if (chunk0 < rem && q.nonEmpty) {  // queue is full, replace items
        var min = q.head._1
        var i = chunk0
        while (i < rem) {
          val value = values(i)
          val key   = keys  (i)
          // if the key is higher than the lowest in the queue...
          if (keyOrd.gt(key, min)) {
            // ...we remove the head of the queue and add the new key
            q.dequeue()
            q += key -> value
            min = q.head._1
          }
          i += 1
        }
      }
      hInK.advance(rem)
      hInV.advance(rem)
      readIntoQueue()
    }

    private def writeFromQueue(): Unit = {
      val rem = min(bufRemain, hOut.available)
      if (rem == 0) return

      val arr     = hOut.array
      var outOffI = hOut.offset
      val q       = result
      var _bufRem = bufRemain
      var i       = 0
      while (i < rem) {
        _bufRem     -= 1
        val _value   = q(_bufRem)
        arr(outOffI) = _value
        outOffI     += 1
        i           += 1
      }
      hOut.advance(rem)
      bufRemain  = _bufRem
    }
  }
}