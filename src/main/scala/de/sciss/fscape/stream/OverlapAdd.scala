/*
 *  OverlapAdd.scala
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

package de.sciss.fscape.stream

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{FilterIn3Impl, WindowedFilterLogicImpl}

import scala.collection.mutable

/** Overlapping window summation. Counter-part to `Sliding`.
  */
object OverlapAdd {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param step   the step size. this is clipped to be `&lt;= 1`. If it is greater
    *               than `size`, parts of the input will be correctly skipped.
    */
  def apply(in: OutD, size: OutI, step: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in   , stage.in0)
    b.connect(size , stage.in1)
    b.connect(step , stage.in2)
    stage.out
  }

  private final class Window(val buf: Array[Double]) {
    var offIn   = 0
    var offOut  = 0
    var size    = buf.length

    def inRemain    : Int = size  - offIn
    def availableOut: Int = offIn - offOut
    def outRemain   : Int = size  - offOut
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {

    val shape = new FanInShape3(
      in0 = InD ("OverlapAdd.in"  ),
      in1 = InI ("OverlapAdd.size"),
      in2 = InI ("OverlapAdd.step"),
      out = OutD("OverlapAdd.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(protected val shape: FanInShape3[BufD, BufI, BufI, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with WindowedFilterLogicImpl[BufD, BufD, FanInShape3[BufD, BufI, BufI, BufD]]
      with FilterIn3Impl[BufD, BufI, BufI, BufD] {

    protected val in0: InD = shape.in0

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    private[this] val windows = mutable.Buffer.empty[Window]

    protected def startNextWindow(inOff: Int): Int = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        step = math.max(1, bufIn2.buf(inOff))
      }
      // println(s"startNextWindow($inOff) -> size = $size, step = $step")
      size
      // math.min(size, step)
      // step
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = {
      if (writeToWinOff == 0) {
        // println(s"adding   window of size $size")
        windows += new Window(new Array[Double](size))
      }
      val win     = windows.last
      val chunk1  = math.min(chunk, win.inRemain)
      // println(s"copying $chunk1 frames to   window ${windows.length - 1} at ${win.offIn}")
      if (chunk1 > 0) {
        Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunk1)
        win.offIn += chunk1
        // remain  -= chunk1
      }
    }

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = {
//      val win = windows.last
//      val res = if (win.inRemain > 0) win.offIn else step
//      println(s"processWindow($writeToWinOff) -> $res")
//      res
      if (flush) windows.last.size else step
    }

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
      Util.clear(bufOut.buf, outOff, chunk)
      var i = 0
      while (i < windows.length) {  // take care of index as we drop windows on the way
        val win = windows(i)
        val chunk1 = math.min(win.availableOut, chunk)
        // println(s"copying $chunk1 frames from window $i at ${win.offOut}")
        if (chunk1 > 0) {
          Util.add(win.buf, win.offOut, bufOut.buf, outOff, chunk1)
          win.offOut += chunk1
        }
        if (win.outRemain == 0) {
          // println(s"removing window $i")
          windows.remove(i)
        } else {
          i += 1
        }
      }
    }
  }
}