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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.FilterIn3Impl

import scala.annotation.tailrec

/** Sliding overlapping window.
  *
  * XXX TODO: should support `step &gt; size` (dropping from input)
  */
object Sliding {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param step   the step size. this is clipped to be `&lt;= 1` and `&lt;= size`
    */
  def apply(in: Outlet[BufD], size: Outlet[BufI], step: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1
    step ~> stage.in2

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
      in0 = Inlet [BufD]("Sliding.in"  ),
      in1 = Inlet [BufI]("Sliding.size"),
      in2 = Inlet [BufI]("Sliding.step"),
      out = Outlet[BufD]("Sliding.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO --- we should try to see if this can be implemented
  // on top of windowed-logic-impl which would make it much simpler
  private final class Logic(protected val shape: FanInShape3[BufD, BufI, BufI, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape) with FilterIn3Impl[BufD, BufI, BufI, BufD] {

    private[this] var inOff         = 0  // regarding `bufIn`
    private[this] var inRemain      = 0
    private[this] var stepRemain    = 0
    private[this] var outOff        = 0  // regarding `bufOut`
    private[this] var outRemain     = 0

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _
    private[this] var windows = Vector.empty[Window]

    private[this] var isNextStep    = true
    private[this] var outSent       = true

    @inline
    private[this] def shouldRead     = inRemain   == 0 && canRead

    /*
      back-pressure algorithm:
      - never begin a step if windows.head is full
      - for example with a constant step size of 1/4 window size,
        this means we halt processing input after window size
        input frames (i.e. with four windows in memory).
     */
    @inline
    private[this] def canPrepareStep = stepRemain == 0 && bufIn0 != null &&
      (windows.isEmpty || windows.head.inRemain > 0)

    @tailrec
    def process(): Unit = {
      var stateChange = false

      // read inlets
      if (shouldRead) {
        readIns()
        inOff       = 0
        inRemain    = bufIn0.size // if (inRemain == 48)
        stateChange = true
      }

      // create new window
      if (canPrepareStep && isNextStep) {
        if (bufIn1 != null && inOff < bufIn1.size) {
          size = math.max(1, bufIn1.buf(inOff))
        }
        if (bufIn2 != null && inOff < bufIn2.size) {
          step = math.max(1, math.min(size, bufIn2.buf(inOff)))
        }
        stepRemain  = step
        val win     = new Window(new Array[Double](size))
        windows   :+= win
        isNextStep  = false
        stateChange = true
      }

      if (windows.nonEmpty) {
        // create new output buffer
        if (outSent) {
          bufOut        = ctrl.borrowBufD()
          outRemain     = bufOut.size
          outOff        = 0
          outSent       = false
          stateChange   = true
        }

        // copy input to windows
        val chunkIn     = math.min(inRemain, stepRemain)
        if (chunkIn > 0) {
          windows.foreach { win =>
            val chunkWin  = math.min(win.inRemain, chunkIn)
            if (chunkWin > 0) {
              Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunkWin)
              win.offIn += chunkWin
            }
          }
          inOff        += chunkIn
          inRemain     -= chunkIn
          stepRemain   -= chunkIn
          if (stepRemain == 0) isNextStep = true
          stateChange   = true
        }

        // copy window to output
        val win           = windows.head
        val flushWin      = inRemain == 0 && isClosed(shape.in0)
        if (flushWin) {
          if (windows.size == 1) {
            //   if there are no other windows coming,
            //   we must make sure `outRemain` is shortened.
            outRemain = math.min(outRemain, win.offIn)
            win.size  = win.offIn

          } else {
            //   if there are other windows coming,
            //   we should zero-pad the window upon flush
            //   in order to guarantee the expected window size
            win.offIn = win.size  // factual zero padding
          }
        }
        val chunkOut = math.min(win.availableOut, outRemain)
        if (chunkOut > 0 || flushWin) {
          Util.copy(win.buf, win.offOut, bufOut.buf, outOff, chunkOut)
          win.offOut   += chunkOut
          outOff       += chunkOut
          outRemain    -= chunkOut
          val dropWin   = win.outRemain == 0
          if (dropWin) windows = windows.tail
          if (chunkOut > 0 || dropWin) {
            stateChange   = true
          }
        }
      }

      // write outlet
      val flushOut = inRemain == 0 && windows.isEmpty && isClosed(shape.in0)
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(shape.out, bufOut)
        } else {
          bufOut.release()
        }
        bufOut      = null
        outSent     = true
        stateChange = true
      }

      if (flushOut && outSent) completeStage()
      else if (stateChange) process()
    }
  }
}