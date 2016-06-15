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

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{FilterIn3Impl, Out1LogicImpl, StageLogicImpl}

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
  }

  private final val name = "Sliding"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {

    override def toString = s"$name@${hashCode.toHexString}"

    val shape = new FanInShape3(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InI (s"$name.step"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO --- we should try to see if this can be implemented
  // on top of windowed-logic-impl which would make it much simpler.
  // XXX TODO --- check that flushIn is correctly handled (we should
  // flush partial windows)
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with Out1LogicImpl[BufD, Shape]
      with FilterIn3Impl[BufD, BufI, BufI, BufD] {

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

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

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
      logStream(s"$this.process()")

      // read inlets
      if (shouldRead) {
        inRemain    = readIns()
        inOff       = 0
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
          bufOut0       = allocOutBuf0()
          outRemain     = bufOut0.size
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
          Util.copy(win.buf, win.offOut, bufOut0.buf, outOff, chunkOut)
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
          bufOut0.size = outOff
          push(shape.out, bufOut0)
        } else {
          bufOut0.release()
        }
        bufOut0      = null
        outSent     = true
        stateChange = true
      }

      if (flushOut && outSent) {
        logStream(s"$this.completeStage()")
        completeStage()
      }
      else if (stateChange) process()
    }
  }
}