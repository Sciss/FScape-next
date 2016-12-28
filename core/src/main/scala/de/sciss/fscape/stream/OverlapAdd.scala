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

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{DemandFilterIn3D, DemandFilterLogic, DemandWindowedLogic, NodeImpl, StageImpl}

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
    var offIn : Int  = 0
    var offOut: Int  = 0
    var size  : Int  = buf.length

    def inRemain    : Int = size  - offIn
    def availableOut: Int = offIn - offOut
    def outRemain   : Int = size  - offOut
  }

  private final val name = "OverlapAdd"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InI (s"$name.step"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with DemandFilterLogic[BufD, Shape]
      with DemandFilterIn3D[BufD, BufI, BufI] {

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    private[this] val windows = mutable.Buffer.empty[Window]

    protected def startNextWindow(): Long = {
      val inOff = auxInOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        step = math.max(1, bufIn2.buf(inOff))
      }
      windows += new Window(new Array[Double](size))
      size  // -> writeToWinRemain
    }

    // var FRAMES_READ = 0

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      val inOff = mainInOff
      // println(s"-- OLAP copyInputToWindow(inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk) $FRAMES_READ")
      // FRAMES_READ += chunk
      val win     = windows.last
      val chunk1  = math.min(chunk, win.inRemain)
      // println(s"OLAP copying $chunk1 frames to   window ${windows.length - 1} at ${win.offIn}")
      if (chunk1 > 0) {
        Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunk1)
        win.offIn += chunk1
      }
    }

    protected def processWindow(writeToWinOff: Long): Long = step

    // var FRAMES_WRITTEN = 0

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      // println(s"-- OLAP copyWindowToOutput(readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk) $FRAMES_WRITTEN")
      // FRAMES_WRITTEN += chunk
      Util.clear(bufOut0.buf, outOff, chunk)
      var i = 0
      while (i < windows.length) {  // take care of index as we drop windows on the way
        val win = windows(i)
        val chunk1 = math.min(win.availableOut, chunk)
        // println(s"OLAP copying $chunk1 frames from window $i at ${win.offOut}")
        if (chunk1 > 0) {
          Util.add(win.buf, win.offOut, bufOut0.buf, outOff, chunk1)
          win.offOut += chunk1
        }
        if (win.outRemain == 0) {
          // println(s"OLAP removing window $i")
          windows.remove(i)
        } else {
          i += 1
        }
      }
    }
  }
}