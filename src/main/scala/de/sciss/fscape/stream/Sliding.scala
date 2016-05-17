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

/** Sliding overlapping window. */
object Sliding {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param step   the step size. this is clipped to be `&lt;= 1` and `&lt;= size`
    */
  def apply(in: Outlet[BufD], size: Outlet[BufI], step: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage(ctrl)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1
    step ~> stage.in2

    stage.out
  }

  private final class Window(val buf: Array[Double], var off: Int)

  private final class Stage(ctrl: Control) extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {
    val shape = new FanInShape3(
      in0 = Inlet [BufD]("Sliding.in"  ),
      in1 = Inlet [BufI]("Sliding.size"),
      in2 = Inlet [BufI]("Sliding.step"),
      out = Outlet[BufD]("Sliding.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape, ctrl)
  }

  private final class Logic(protected val shape: FanInShape3[BufD, BufI, BufI, BufD],
                            protected val ctrl: Control)
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
    @inline
    private[this] def canPrepareStep = stepRemain == 0 && bufIn0 != null

    @tailrec
    protected def process(): Unit = {
      var stateChange = false

      if (shouldRead) {
        readIns()
        inOff       = 0
        inRemain    = bufIn0.size
        stateChange = true
      }
      if (canPrepareStep) {
        if (isNextStep) {
          if (bufIn1 != null && inOff < bufIn1.size) {
            size = math.max(1, bufIn1.buf(inOff))
          }
          if (bufIn2 != null && inOff < bufIn2.size) {
            step = math.max(1, math.min(size, bufIn2.buf(inOff)))
          }
          stepRemain = step
          val win = new Window(new Array[Double](size), off = 0)
          windows :+= win
          isNextStep  = false
          stateChange = true
        }

        ???
      }

      if (windows.nonEmpty) {
        if (outSent) {
          bufOut        = ctrl.borrowBufD()
          outRemain     = bufOut.size
          outOff        = 0
          outSent       = false
          stateChange   = true
        }

        val win       = windows.head
        val winRemain = win.buf.length - win.off
        val chunk     = math.min(winRemain, outRemain)
        if (chunk > 0) {
          Util.copy(win.buf, win.off, bufOut.buf, outOff, chunk)
          win.off      += chunk
          outOff       += chunk
          outRemain    -= chunk
          if (win.off == win.buf.length) windows = windows.tail
          stateChange   = true
        }
      }

      val flushOut = inRemain == 0 && windows.isEmpty && isClosed(shape.in0)
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(shape.out, bufOut)
        } else {
          bufOut.release()(ctrl)
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