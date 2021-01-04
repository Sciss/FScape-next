/*
 *  OverlapAdd.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.{max, min}

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
    val stage0  = new Stage(b.layer)
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

  private type Shp = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape3(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InI (s"$name.step"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers[Shp](name, layer, shape) {

    private[this] val hIn   = Handlers.InDMain  (this, shape.in0)
    private[this] val hSize = Handlers.InIAux   (this, shape.in1)(max(1, _))
    private[this] val hStep = Handlers.InIAux   (this, shape.in2)(max(1, _))
    private[this] val hOut  = Handlers.OutDMain (this, shape.out)

    private[this] var writeToWinOff     = 0
    private[this] var writeToWinRemain  = 0
    private[this] var readFromWinOff    = 0
    private[this] var readFromWinRemain = 0
    private[this] var isNextWindow      = true

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    private[this] val windows = mutable.Buffer.empty[Window]

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    private def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hStep.hasNext
      if (ok) {
        size = hSize.next()
        step = hStep.next()
        windows += new Window(new Array[Double](size))
      }
      ok
    }

    private def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      val win     = windows.last
      val chunk1  = min(chunk, win.inRemain)
      if (chunk1 > 0) {
        hIn.nextN(win.buf, win.offIn, chunk1)
        win.offIn += chunk1
        val chunk2 = chunk - chunk1
        if (chunk2 > 0) {
          hIn.skip(chunk2)
        }
      } else {
        hIn.skip(chunk)
      }
    }

    private def copyWindowToOutput(readFromWinOff: Long, chunk: Int): Unit = {
      val out     = hOut.array
      val outOff  = hOut.offset
      Util.clear(out, outOff, chunk)
      var i = 0
      while (i < windows.length) {  // take care of index as we drop windows on the way
        val win     = windows(i)
        val chunk1  = math.min(win.availableOut, chunk)
        if (chunk1 > 0) {
          Util.add(win.buf, win.offOut, out, outOff, chunk1)
          win.offOut += chunk1
        }
        if (win.outRemain == 0) {
          windows.remove(i)
        } else {
          i += 1
        }
      }
      hOut.advance(chunk)
    }

    @tailrec
    protected def process(): Unit = {
      var stateChange = false

      if (readFromWinRemain == 0) { // aka can-write-to-window
        if (hIn.isDone) {
          if (windows.nonEmpty) {
            var i = 0
            var maxAvail = 0
            while (i < windows.length) { // take care of index as we drop windows on the way
              val win   = windows(i)
              win.size  = win.offIn
              val n     = win.availableOut
              if (n > maxAvail) maxAvail = n
              i += 1
            }
            readFromWinRemain = maxAvail
          } else {
            if (hOut.flush()) completeStage()
            return
          }

        } else {
          if (isNextWindow) {
            if (!tryObtainWinParams()) return
            writeToWinRemain  = size // startNextWindow()
            isNextWindow      = false
            stateChange       = true
          }

          val chunk = min(writeToWinRemain, hIn.available)
          if (chunk > 0) {
            // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
            if (chunk > 0) {
              copyInputToWindow(writeToWinOff = writeToWinOff, chunk = chunk)
              writeToWinOff    += chunk
              writeToWinRemain -= chunk
              stateChange       = true
            }

            if (writeToWinRemain == 0) {
              readFromWinRemain = step // processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
              writeToWinOff     = 0
              readFromWinOff    = 0
              isNextWindow      = true
              stateChange       = true
              // auxInOff         += 1
              // auxInRemain      -= 1
              // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
            }
          }
        }
      }

      if (readFromWinRemain > 0) {
        val chunk = min(readFromWinRemain, hOut.available)
        if (chunk > 0) {
          // logStream(s"readFromWindow(); readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk")
          copyWindowToOutput(readFromWinOff = readFromWinOff, chunk = chunk)
          readFromWinOff    += chunk
          readFromWinRemain -= chunk
          stateChange        = true
        }
      }

      if (stateChange) process()
    }
  }
}