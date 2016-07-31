/*
 *  Sliding.scala
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
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn3DImpl, FilterLogicImpl, StageImpl, StageLogicImpl}

import scala.collection.mutable

/** Sliding overlapping window. */
object Sliding {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param step   the step size. this is clipped to be `&lt;= 1`.
    *               If step size is larger than window size, frames in
    *               the input are skipped.
    */
  def apply(in: OutD, size: OutI, step: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(step, stage.in2)
    stage.out
  }

  private final class Window(val buf: Array[Double]) {
    var offIn   = 0
    var offOut  = 0
    var size    = buf.length

    def inRemain    : Int = size  - offIn
    def availableOut: Int = offIn - offOut
    def outRemain   : Int = size  - offOut

    override def toString = s"Window(offIn = $offIn, offOut = $offOut, size = $size)"
  }

  private final val name = "Sliding"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InI (s"$name.step"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with ChunkImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    private[this] val windows = mutable.Buffer.empty[Window]

    private[this] var isNextWindow  = true
    private[this] var stepRemain    = 0

    /*
      back-pressure algorithm:
      - never begin a step if windows.head is full
      - for example with a constant step size of 1/4 window size,
        this means we halt processing input after window size
        input frames (i.e. with four windows in memory).
     */
    @inline
    private def canPrepareStep = stepRemain == 0 && bufIn0 != null &&
      (windows.isEmpty || windows.head.inRemain > 0)

    protected def shouldComplete(): Boolean = inputsEnded && windows.isEmpty

    protected def processChunk(): Boolean = {
      var stateChange = false

//      println(s"--- SLID canPrepareStep = $canPrepareStep; isNextWindow = $isNextWindow")
      if (canPrepareStep && isNextWindow) {
        stepRemain    = startNextWindow(inOff = inOff)
//        println(s"--- SLID stepRemain = $stepRemain")
        isNextWindow  = false
        stateChange   = true
      }

      val chunkIn = math.min(stepRemain, inRemain)
//      println(s"--- SLID chunkIn = $chunkIn")
      if (chunkIn > 0) {
        /* val chunk1 = */ copyInputToWindows(chunkIn)
//        println(s"--- SLID copyInputToWindows($chunkIn) -> $chunk1")
//        if (chunk1 > 0) {
          inOff       += chunkIn // chunk1
          inRemain    -= chunkIn // chunk1
          stepRemain  -= chunkIn // chunk1
          stateChange  = true

          if (stepRemain == 0) {
            isNextWindow = true
            stateChange  = true
          }
//        }
      }
      else if (inputsEnded) { // flush
//        println(s"--- SLID inputsEnded")
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
//      println(s"--- SLID chunkOut = $chunkOut")
      if (chunkOut > 0) {
        val chunk1 = copyWindowsToOutput(chunkOut)
        if (chunk1 > 0) {
          // println(s"--- SLID copyWindowsToOutput($chunkOut) -> $chunk1")
          outOff      += chunk1
          outRemain   -= chunk1
          stateChange  = true
        }
      }

      stateChange
    }

    @inline
    private def startNextWindow(inOff: Int): Int = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        step = math.max(1, bufIn2.buf(inOff))
      }
      windows += new Window(new Array[Double](size))
      step  // -> writeToWinRemain
    }

    @inline
    private def copyInputToWindows(chunk: Int): Unit /* Int */ = {
      var i = 0
//      var res = 0
      while (i < windows.length) {
        val win = windows(i)
        val chunk1 = math.min(win.inRemain, chunk)
        if (chunk1 > 0) {
          Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunk1)
          // println(s"SLID copying $chunk1 frames from in at $inOff to window $i at ${win.offIn}")
          win.offIn += chunk1
//          res = math.max(res, chunk1)
        }
        i += 1
      }
//      res
    }

    @inline
    private def copyWindowsToOutput(chunk: Int): Int = {
      var i = 0
      var chunk0  = chunk
      var outOff0 = outOff
      while (chunk0 > 0 && i < windows.length) {  // take care of index as we drop windows on the way
        val win     = windows(i)
        val chunk1  = math.min(chunk0, win.availableOut)
        if (chunk1 > 0) {
          Util.copy(win.buf, win.offOut, bufOut0.buf, outOff0, chunk1)
          // println(s"SLID copying $chunk1 frames from window $i at ${win.offOut} to out at $outOff0")
          win.offOut += chunk1
          chunk0     -= chunk1
          outOff0    += chunk1
        }
        if (win.outRemain == 0) {
          // println("SLID dropping window 0")
          windows.remove(i)
        } else {
          i = windows.length
        }
      }
      chunk - chunk0
    }
  }
}