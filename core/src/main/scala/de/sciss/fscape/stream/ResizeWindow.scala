/*
 *  ResizeWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.stream.impl.{FilterIn4DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

object ResizeWindow {
  /** Resizes the windowed input signal by trimming each
    * windows boundaries (if `start` is greater than zero
    * or `stop` is less than zero) or padding the boundaries
    * with zeroes (if `start` is less than zero or `stop` is
    * greater than zero). The output window size is thus
    * `size - start + stop`.
    *
    * @param in     the signal to window and resize
    * @param size   the input window size
    * @param start  the delta window size at the output window's beginning
    * @param stop   the delta window size at the output window's ending
    */
  def apply(in: OutD, size: OutI, start: OutI, stop: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(start , stage.in2)
    b.connect(stop  , stage.in3)

    stage.out
  }

  private final val name = "ResizeWindow"

  private type Shape = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"   ),
      in1 = InI (s"$name.size" ),
      in2 = InI (s"$name.start"),
      in3 = InI (s"$name.stop" ),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn4DImpl[BufD, BufI, BufI, BufI] {

    private[this] var winBuf      : Array[Double] = _
    private[this] var winInSize   : Int = _
    private[this] var winKeepSize : Int = _
    private[this] var winOutSize  : Int = _
    private[this] var startPos    : Int = _
    private[this] var startNeg    : Int = _
    private[this] var stopPos     : Int = _
    private[this] var stopNeg     : Int = _

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = winKeepSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winInSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val start = bufIn2.buf(inOff)
        startPos  = math.max(0, start)
        startNeg  = math.min(0, start)
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        val stop = bufIn3.buf(inOff)
        stopPos  = math.max(0, stop)
        stopNeg  = math.min(0, stop)
      }
      winKeepSize = math.max(1, winInSize - startPos + stopNeg)
      if (winKeepSize != oldSize) {
        winBuf = new Array[Double](winKeepSize)
      }
      winOutSize = math.max(1, winInSize - (startPos + startNeg) + (stopPos + stopNeg))

      // println(s"next: winKeepSize $winKeepSize, winOutSize $winOutSize, winInSize $winInSize")
      winInSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit = {
      val writeOffI = writeToWinOff.toInt
      // ex. startPos = 10, writeToWinOff = 4, chunk = 12, inOff = 7
      // then skipStart becomes 6, inOff1 becomes 13, winOff1 becomes 4 + 6 - 10 = 0, chunk1 becomes 6
      // and we effectively begin writing to the buffer begin having skipped 10 input frames.
      val skipStart = math.max(0, startPos - writeOffI)
      if (skipStart > chunk) return
      
      val inOff1    = inOff + skipStart
      val winOff1   = writeOffI + skipStart - startPos
      val chunk1    = math.min(chunk - skipStart, winKeepSize - winOff1)
      if (chunk1 <= 0) return

      Util.copy(bufIn0.buf, inOff1, winBuf, winOff1, chunk1)
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val readOffI  = readFromWinOff.toInt
      val arr       = bufOut0.buf
      val zeroStart = math.min(chunk, math.max(0, -startNeg - readOffI))
      if (zeroStart > 0) {
        Util.fill(arr, outOff, zeroStart, 0.0)
      }
      val winOff1   = readOffI + zeroStart + startNeg
      val outOff1   = outOff + zeroStart
      val chunk1    = chunk - zeroStart
      val chunk2    = math.min(chunk1, math.max(0, winKeepSize - winOff1))
      if (chunk2 > 0) {
        Util.copy(winBuf, winOff1, arr, outOff1, chunk2)
//        var i = outOff1
//        val j = i + chunk2
//        while (i < j) {
//          arr(i) = if ((i % 2) == 0) 0.5 else -0.5
//          i += 1
//        }
      }

      val zeroStop  = chunk - (chunk2 + zeroStart) // - chunk1
      if (zeroStop > 0) {
        val outOff2 = outOff1 + chunk2
        Util.fill(arr, outOff2, zeroStop, 0.0)
      }

      // println(f"out: winOff $readFromWinOff%4d, outOff $outOff%4d, chunk $chunk%4d >> zeroStart $zeroStart%4d, zeroStop $zeroStop%4d")
    }

    protected def processWindow(writeToWinOff: Long): Long = winOutSize
  }
}