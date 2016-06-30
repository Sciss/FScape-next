/*
 *  OffsetOverlapAdd.scala
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
import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn5DImpl, FilterLogicImpl, StageImpl, StageLogicImpl}

import scala.collection.mutable

/** Overlapping window summation with offset (fuzziness) that can be modulated. */
object OffsetOverlapAdd {
  /**
    * @param in         the signal to window
    * @param size       the window size. this is clipped to be `&lt;= 1`
    * @param step       the step size. this is clipped to be `&lt;= 1`. If it is greater
    *                   than `size`, parts of the input will be correctly skipped.
    * @param offset     frame offset by which each input window is shifted. Can change from window to window.
    * @param minOffset  minimum (possibly negative) offset to reserve space for. Any `offset` value
    *                   encountered smaller than this will be clipped to `minOffset`.
    */
  def apply(in: OutD, size: OutI, step: OutI, offset: OutI, minOffset: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(step      , stage.in2)
    b.connect(offset    , stage.in3)
    b.connect(minOffset , stage.in4)
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

  private final val name = "OffsetOverlapAdd"

  private type Shape = FanInShape5[BufD, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape5(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.size"     ),
      in2 = InI (s"$name.step"     ),
      in3 = InI (s"$name.offset"   ),
      in4 = InI (s"$name.minOffset"),
      out = OutD(s"$name.out"      )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with ChunkImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn5DImpl[BufD, BufI, BufI, BufI, BufI] {

    private[this] var size     : Int  = _
    private[this] var step     : Int  = _
    private[this] var offset   : Int  = _
    private[this] var minOffset: Int  = _

    private[this] var init = false

    private[this] val windows = mutable.Buffer.empty[Window]

    protected def shouldComplete(): Boolean = ???

    private[this] var isNextWindow  = true
    private[this] var stepRemain    = 0

    @inline
    private def canPrepareStep: Boolean = stepRemain == 0 && bufIn0 != null && ???

    protected def processChunk(): Boolean = {
      var stateChange = false

      if (canPrepareStep && isNextWindow) {
        // println("SLID next-window")
        stepRemain    = startNextWindow(inOff = inOff)
        windows      += new Window(new Array[Double](size))
        isNextWindow  = false
        stateChange   = true
      }

      val chunkIn = math.min(stepRemain, inRemain)
      if (chunkIn > 0) {
        ???
//        val chunk1 = copyInputToWindows(chunkIn)
//        if (chunk1 > 0) {
//          // println(s"--- SLID copyInputToWindows($chunkIn) -> $chunk1")
//          inOff       += chunk1
//          inRemain    -= chunk1
//          stepRemain  -= chunk1
//          stateChange  = true
//
//          if (stepRemain == 0) {
//            isNextWindow = true
//            stateChange  = true
//          }
//        }
      }
      else if (inputsEnded) { // flush
      var i = 0
        while (i < windows.length - 1) {
          val win = windows(i)
          if (win.inRemain > 0) {
            win.offIn   = win.size    // 'zeroed'
            stateChange = true
          }
          i += 1
        }
        if (windows.nonEmpty) {
          val win = windows.last
          if (win.inRemain > 0) {
            win.size    = win.offIn   // 'trimmed'
            stateChange = true
          }
        }
      }

      val chunkOut = outRemain
      if (chunkOut > 0) {
        ???
//        val chunk1 = copyWindowsToOutput(chunkOut)
//        if (chunk1 > 0) {
//          // println(s"--- SLID copyWindowsToOutput($chunkOut) -> $chunk1")
//          outOff      += chunk1
//          outRemain   -= chunk1
//          stateChange  = true
//        }
      }

      stateChange
    }

    private def startNextWindow(inOff: Int): Int = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        step = math.max(1, bufIn2.buf(inOff))
      }
      if (init) {
        minOffset = bufIn4.buf(inOff)
        init      = false
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        offset = math.max(minOffset, bufIn3.buf(inOff))
      }
      size  // -> writeToWinRemain
    }

    private def copyInputToWindows(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = {
      if (writeToWinOff == 0) {
        windows += new Window(new Array[Double](size))
      }
      val win     = windows.last
      val chunk1  = math.min(chunk, win.inRemain)
      if (chunk1 > 0) {
        Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunk1)
        win.offIn += chunk1
      }
    }

    private def processWindow(writeToWinOff: Int): Int = {
      val res = /* if (flush) {
        if (windows.isEmpty) 0 else math.max(step, windows.maxBy(_.availableOut).availableOut)
      } else */ step

      // println(s"OLAP processWindow($writeToWinOff, $flush) -> $res")
      res // -> readFromWinRemain
    }

    private def copyWindowsToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
      Util.clear(bufOut0.buf, outOff, chunk)
      var i = 0
      while (i < windows.length) {  // take care of index as we drop windows on the way
      val win = windows(i)
        val chunk1 = math.min(win.availableOut, chunk)
        if (chunk1 > 0) {
          Util.add(win.buf, win.offOut, bufOut0.buf, outOff, chunk1)
          win.offOut += chunk1
        }
        if (win.outRemain == 0) {
          windows.remove(i)
        } else {
          i += 1
        }
      }
    }
  }
}