/*
 *  OverlapAdd.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{DemandChunkImpl, DemandFilterIn3D, DemandFilterLogic, NodeImpl, StageImpl}

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
    extends NodeImpl(name, layer, shape)
      with DemandChunkImpl[Shp] /* DemandWindowedLogic[Shape] */
      with DemandFilterLogic[BufD, Shp]
      with DemandFilterIn3D[BufD, BufI, BufI] {

    private[this] var writeToWinOff     = 0L
    private[this] var writeToWinRemain  = 0L
    private[this] var readFromWinOff    = 0L
    private[this] var readFromWinRemain = 0L
    private[this] var isNextWindow      = true

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    private[this] val windows = mutable.Buffer.empty[Window]

    private  def startNextWindow(): Long = {
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

    private def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      val inOff   = mainInOff
      val win     = windows.last
      val chunk1  = math.min(chunk, win.inRemain)
      if (chunk1 > 0) {
        Util.copy(bufIn0.buf, inOff, win.buf, win.offIn, chunk1)
        win.offIn += chunk1
      }
    }

    private def processWindow(writeToWinOff: Long): Long = step

    private def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      Util.clear(bufOut0.buf, outOff, chunk)
      var i = 0
      while (i < windows.length) {  // take care of index as we drop windows on the way
        val win     = windows(i)
        val chunk1  = math.min(win.availableOut, chunk)
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

    @inline
    private[this] def canWriteToWindow  = readFromWinRemain == 0 && inValid

    protected def processChunk(): Boolean = {
      var stateChange = false

//      if (inputsEnded) {
//        println("AQUI")
//      }

      if (canWriteToWindow) {
        val flushIn0 = inputsEnded // inRemain == 0 && shouldComplete()

        if (flushIn0) {
          if (readFromWinRemain == 0 && windows.nonEmpty) {
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
          }

        } else {
          if (isNextWindow) {
            writeToWinRemain  = startNextWindow()
            isNextWindow      = false
            stateChange       = true
          }

          val chunk     = math.min(writeToWinRemain, mainInRemain).toInt
          if (chunk > 0) {
            // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
            if (chunk > 0) {
              copyInputToWindow(writeToWinOff = writeToWinOff, chunk = chunk)
              mainInOff        += chunk
              mainInRemain     -= chunk
              writeToWinOff    += chunk
              writeToWinRemain -= chunk
              stateChange       = true
            }

            if (writeToWinRemain == 0) {
              readFromWinRemain = processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
              writeToWinOff     = 0
              readFromWinOff    = 0
              isNextWindow      = true
              stateChange       = true
              auxInOff         += 1
              auxInRemain      -= 1
              // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
            }
          }
        }
      }

      if (readFromWinRemain > 0) {
        val chunk = math.min(readFromWinRemain, outRemain).toInt
        if (chunk > 0) {
          // logStream(s"readFromWindow(); readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk")
          copyWindowToOutput(readFromWinOff = readFromWinOff, outOff = outOff, chunk = chunk)
          readFromWinOff    += chunk
          readFromWinRemain -= chunk
          outOff            += chunk
          outRemain         -= chunk
          stateChange        = true
        }
      }

      stateChange
    }

    protected def shouldComplete(): Boolean = inputsEnded && windows.isEmpty // writeToWinOff == 0 && readFromWinRemain == 0
  }
}