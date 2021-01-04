/*
 *  TransposeMatrix.scala
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

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

object TransposeMatrix {
  def apply[A, E <: BufElem[A]](in: Outlet[E], rows: OutI, columns: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in     , stage.in0)
    b.connect(rows   , stage.in1)
    b.connect(columns, stage.in2)
    stage.out
  }

  private final val name = "TransposeMatrix"

  private type Shp[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"     ),
      in1 = InI       (s"$name.rows"   ),
      in2 = InI       (s"$name.columns"),
      out = Outlet[E] (s"$name.out"    )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hRows = Handlers.InIAux(this, shape.in1)(math.max(1, _))
    private[this] val hCols = Handlers.InIAux(this, shape.in2)(math.max(1, _))

    private[this] var winSize: Int = _

//    private[this] var bitSet : mutable.BitSet = _

    protected def winBufSize: Int = winSize * 2

    override protected def readWinSize  : Long = winSize
    override protected def writeWinSize : Long = winSize

    protected def tryObtainWinParams(): Boolean = {
      val ok = hRows.hasNext && hCols.hasNext
      if (ok) {
        val rows  = hRows.next()
        val cols  = hCols.next()
        winSize   = rows * cols
        // if (winSize != oldSize) {
        //   bitSet  = new mutable.BitSet(winSize)
        // }
      }
      ok
    }

    override protected def writeFromWindow(n: Int): Unit = {
      val offI = writeOff.toInt + winSize
      hOut.nextN(winBuf, offI, n)
    }

    protected def processWindow(): Unit = {
      val w       = winBuf
      val colsOut = hRows.value
      val colsIn  = hCols.value
      val sz      = winSize // = rows * cols

      var i     = 0
      var j     = sz  // offset for output
      val jump  = 1 - sz
      var stop  = colsIn
      while (i < sz) {
        w(j) = w(i)
        i += 1
        j += colsOut
        if (i == stop) {
          j    += jump
          stop += colsIn
        }
      }
    }

// a heavy performance just to save half of the win-buf size... not worth it
//
//    protected def processWindow(writeToWinOff: Long): Long = {
//      // cf. https://en.wikipedia.org/wiki/In-place_matrix_transposition
//      // translated from http://www.geeksforgeeks.org/inplace-m-x-n-size-matrix-transpose/
//      val a     = winBuf
//      val size  = winSize
//      if (writeToWinOff < size) {
//        val writeOffI = writeToWinOff.toInt
//        Util.clear(a, writeOffI, size - writeOffI)
//      }
//      val sizeM = size - 1
//      val b     = bitSet
//      val r     = rows
//      b.clear()
//      b.add(0)
//      b.add(sizeM)
//      var i = 1 // Note that first and last elements won't move
//      while (i < sizeM) {
//        if (i % 10000 == 0) println(s"--- $i" )
//        val cycleBegin = i
//        var t          = a(i)
//        do {
//          // Input matrix [r x c]
//          // Output matrix 1
//          // i_new = (i*r)%(N-1)
//          val next = ((i.toLong * r) % sizeM).toInt
//          val t1   = a(next)
//          a(next)  = t
//          t        = t1
//          b.add(i)
//          i        = next
//        }
//        while (i != cycleBegin)
//
//        // Get Next Move (what about querying random location?)
//        i = 1
//        while (i < sizeM && b.contains(i)) i += 1
//      }
//
//      size
//    }
  }
}