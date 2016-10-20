/*
 *  MatrixInMatrix.scala
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

import akka.stream.{Attributes, FanInShape8}
import de.sciss.fscape.stream.impl.{DemandFilterIn8D, DemandFilterLogic, DemandWindowedLogic, NodeImpl, StageImpl}

object MatrixInMatrix {
  def apply(in: OutD, rowsOuter: OutI, columnsOuter: OutI, rowsInner: OutI, columnsInner: OutI,
            rowStep: OutI, columnStep: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in          , stage.in0)
    b.connect(rowsOuter   , stage.in1)
    b.connect(columnsOuter, stage.in2)
    b.connect(rowsInner   , stage.in3)
    b.connect(columnsInner, stage.in4)
    b.connect(rowStep     , stage.in5)
    b.connect(columnStep  , stage.in6)
    b.connect(mode        , stage.in7)
    stage.out
  }

  private final val name = "MatrixInMatrix"

  private type Shape = FanInShape8[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
      in0 = InD (s"$name.in"          ),
      in1 = InI (s"$name.rowsOuter"   ),
      in2 = InI (s"$name.columnsOuter"),
      in3 = InI (s"$name.rowsInner"   ),
      in4 = InI (s"$name.columnsInner"),
      in5 = InI (s"$name.rowStep"     ),
      in6 = InI (s"$name.columnStep"  ),
      in7 = InI (s"$name.mode"        ),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with DemandFilterLogic[BufD, Shape]
      with DemandFilterIn8D[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI] {

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    protected def startNextWindow(): Int = {
      val inOff = auxInOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        step = math.max(1, bufIn2.buf(inOff))
      }
      ??? // windows += new Window(new Array[Double](size))
      size  // -> writeToWinRemain
    }

    // var FRAMES_READ = 0

    protected def copyInputToWindow(writeToWinOff: Int, chunk: Int): Unit = {
      ???
//      val inOff = mainInOff
//      val win     = windows.last
//      val chunk1  = math.min(chunk, win.inRemain)
//      // println(s"OLAP copying $chunk1 frames to   window ${windows.length - 1} at ${win.offIn}")
//      if (chunk1 > 0) {
//        Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunk1)
//        win.offIn += chunk1
//      }
    }

    protected def processWindow(writeToWinOff: Int): Int = ??? // step

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
//      Util.clear(bufOut0.buf, outOff, chunk)
//      var i = 0
//      while (i < windows.length) {  // take care of index as we drop windows on the way
//      val win = windows(i)
//        val chunk1 = math.min(win.availableOut, chunk)
//        // println(s"OLAP copying $chunk1 frames from window $i at ${win.offOut}")
//        if (chunk1 > 0) {
//          Util.add(win.buf, win.offOut, bufOut0.buf, outOff, chunk1)
//          win.offOut += chunk1
//        }
//        if (win.outRemain == 0) {
//          // println(s"OLAP removing window $i")
//          windows.remove(i)
//        } else {
//          i += 1
//        }
//      }
    }
  }
}