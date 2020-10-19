/*
 *  SortWindow.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InMain, OutMain}
import de.sciss.fscape.stream.impl.logic.WindowedInA1A2OutB
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.math.max

object SortWindow {
  def apply[A, K <: BufElem[A], B, V <: BufElem[B]](keys: Outlet[K], values: Outlet[V], size: OutI)
                                                   (implicit b: Builder,
                                                    keyTpe  : StreamType[A, K],
                                                    valueTpe: StreamType[B, V]): Outlet[V] = {
    val stage0  = new Stage[A, K, B, V](b.layer)
    val stage   = b.add(stage0)
    b.connect(keys  , stage.in0)
    b.connect(values, stage.in1)
    b.connect(size  , stage.in2)
    stage.out
  }

  private final val name = "SortWindow"

  private type Shp[K, V] = FanInShape3[K, V, BufI, V]

  private final class Stage[A, K <: BufElem[A], B, V <: BufElem[B]](layer: Layer)
                             (implicit ctrl: Control, keyTpe: StreamType[A, K], valueTpe: StreamType[B, V])
    extends StageImpl[Shp[K, V]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet [K](s"$name.keys"  ),
      in1 = Inlet [V](s"$name.values"),
      in2 = InI      (s"$name.size"  ),
      out = Outlet[V](s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, K, B, V](shape, layer)
  }

  private final class Logic[A, K <: BufElem[A], B, V <: BufElem[B]](shape: Shp[K, V], layer: Layer)
                             (implicit ctrl: Control,
                              protected val a1Tpe: StreamType[A, K],
                              protected val a2Tpe: StreamType[B, V])
    extends Handlers(name, layer, shape) with WindowedInA1A2OutB[A, K, B, V, B, V, (A, B)] {

    import a1Tpe.{ordering => keyOrd}

    protected     val hIn1  : InMain  [A, K]  = InMain  [A, K](this, shape.in0)
    protected     val hIn2  : InMain  [B, V]  = InMain  [B, V](this, shape.in1)
    protected     val hOut  : OutMain [B, V]  = OutMain [B, V](this, shape.out)
    private[this] val hSize : InIAux          = InIAux        (this, shape.in2)(max(0 , _))

    protected def bTpe: StreamType[B, V] = a2Tpe

    // highest priority = lowest keys
    private[this] object SortedKeys extends Ordering[(A, B)] {
      def compare(x: (A, B), y: (A, B)): Int = keyOrd.compare(x._1, y._1)
    }

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext
      if (ok) hSize.next()
      ok
    }

    protected def winBufSize: Int = hSize.value

    override protected val fullLastWindow: Boolean = false

    protected def clearWindowTail(): Unit = ()

    protected def newWindowBuffer(n: Int): Array[(A, B)] = new Array(n)

    protected def readIntoWindow(chunk: Int): Unit = {
      val inK = hIn1.array
      val inV = hIn2.array
      val out = winBuf
      var ik  = hIn1.offset
      var iv  = hIn2.offset
      var j   = readOff.toInt
      val k   = ik + chunk
      while (ik < k) {
        val tup = (inK(ik), inV(iv))
        out(j)  = tup
        ik += 1
        iv += 1
        j  += 1
      }
      hIn1.advance(chunk)
      hIn2.advance(chunk)
    }

    protected def processWindow(): Unit =
      util.Arrays.sort(winBuf, 0, readOff.toInt, SortedKeys)

    protected def writeFromWindow(chunk: Int): Unit = {
      val in  = winBuf
      val out = hOut.array
      var i   = writeOff.toInt
      var j   = hOut.offset
      val k   = i + chunk
      while (i < k) {
        val tup = in(i)
        out(j) = tup._2
        i += 1
        j += 1
      }
      hOut.advance(chunk)
    }
  }
}