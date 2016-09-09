/*
 *  TransposeMatrix.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3DImpl, FilterLogicImpl, StageImpl, StageLogicImpl, WindowedLogicImpl}

import scala.collection.mutable

object TransposeMatrix {
  def apply(in: OutD, rows: OutI, columns: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in     , stage.in0)
    b.connect(rows   , stage.in1)
    b.connect(columns, stage.in2)
    stage.out
  }

  private final val name = "TransposeMatrix"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"     ),
      in1 = InI (s"$name.rows"   ),
      in2 = InI (s"$name.columns"),
      out = OutD(s"$name.out"    )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var winBuf : Array[Double] = _
    private[this] var rows   : Int = _
    private[this] var columns: Int = _
    private[this] var winSize: Int = _
    private[this] var bitSet : mutable.BitSet = _

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        rows = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        columns = math.max(1, bufIn2.buf(inOff))
      }
      winSize = rows * columns
      if (winSize != oldSize) {
        winBuf  = new Array[Double] (winSize)
        bitSet  = new mutable.BitSet(winSize)
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Int): Int = {
      // cf. https://en.wikipedia.org/wiki/In-place_matrix_transposition
      // translated from http://www.geeksforgeeks.org/inplace-m-x-n-size-matrix-transpose/
      val a     = winBuf
      val size  = winSize
      if (writeToWinOff < size) Util.clear(a, writeToWinOff, size - writeToWinOff)
      val sizeM = size - 1
      val b     = bitSet
      val r     = rows
      b.clear()
      b.add(0)
      b.add(sizeM)
      var i = 1 // Note that first and last elements won't move
      while (i < sizeM) {
        if (i % 10000 == 0) println(s"--- $i" )
        val cycleBegin = i
        var t          = a(i)
        do {
          // Input matrix [r x c]
          // Output matrix 1
          // i_new = (i*r)%(N-1)
          val next = (i * r) % sizeM
          val t1   = a(next)
          a(next)  = t
          t        = t1
          b.add(i)
          i        = next
        }
        while (i != cycleBegin)

        // Get Next Move (what about querying random location?)
        i = 1
        while (i < sizeM && b.contains(i)) i += 1
      }

      size
    }
  }
}