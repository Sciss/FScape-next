/*
 *  ResizeWindow.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet, Outlet}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.math.{max, min}

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
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, start: OutI, stop: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(start , stage.in2)
    b.connect(stop  , stage.in3)

    stage.out
  }

  private final val name = "ResizeWindow"

  private type Shp[E] = FanInShape4[E, BufI, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape4(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InI       (s"$name.start"),
      in3 = InI       (s"$name.stop" ),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control,
                                                tpe: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize   = Handlers.InIAux(this, shape.in1)(max(0 , _))
    private[this] val hStart  = Handlers.InIAux(this, shape.in2)()
    private[this] val hStop   = Handlers.InIAux(this, shape.in3)()

    private[this] var startPos    : Int = -1
    private[this] var startNeg    : Int = _
//    private[this] var stopPos     : Int = -1
//    private[this] var stopNeg     : Int = _
    private[this] var winInSize   : Int = _
    private[this] var winOutSize  : Int = _
    private[this] var _winBufSize : Int = _

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hStart.hasNext && hStop.hasNext
      if (ok) {
        val _winInSize  = hSize .next()
        val start       = hStart.next()
        val stop        = hStop .next()

        val _startPos   = max(0, start)
        startNeg        = min(0, start)
//        stopPos         = max(0, stop)
        val _stopNeg    = min(0, stop)

        startPos        = _startPos
//        stopNeg         = _stopNeg

        winInSize       = _winInSize
        _winBufSize     = max(0 /*1*/, _winInSize - _startPos + _stopNeg)
        winOutSize      = max(0 /*1*/, _winInSize - start + stop)
      }
      ok
    }

    protected def winBufSize: Int = _winBufSize

    override protected def readWinSize  : Long = winInSize
    override protected def writeWinSize : Long = winOutSize

    protected def processWindow(): Unit = ()

    override protected def clearWindowTail(): Unit = {
      val writeOffI = readOff.toInt
      val chunk     = winInSize - writeOffI
      val skipStart = max(0, startPos - writeOffI)
      if (skipStart > chunk) return

      val winOff1   = writeOffI + skipStart - startPos
      val chunk1    = /*if (win == null) 0 else*/ min(chunk - skipStart, _winBufSize - winOff1)
      if (chunk1 <= 0) return

      tpe.clear(winBuf, winOff1, chunk1)
    }

    override protected def readIntoWindow(chunk: Int): Unit = {
      val writeOffI = readOff.toInt // writeToWinOff.toInt
      // ex. startPos = 10, writeToWinOff = 4, chunk = 12, inOff = 7
      // then skipStart becomes 6, inOff1 becomes 13, winOff1 becomes 4 + 6 - 10 = 0, chunk1 becomes 6
      // and we effectively begin writing to the buffer begin having skipped 10 input frames.
      val skipStart = max(0, startPos - writeOffI)

      if (skipStart <= chunk) {
        val winOff1 = writeOffI + skipStart - startPos
        val chunk1  = min(chunk - skipStart, _winBufSize - winOff1)
        if (chunk1 > 0) {
          if (skipStart > 0) hIn.skip(skipStart)
          hIn.nextN(winBuf, winOff1, chunk1)
          val skipStop = chunk - (chunk1 + skipStart)
          if (skipStop  > 0) hIn.skip(skipStop)
        } else {
          hIn.skip(chunk)
        }
      } else {
        hIn.skip(chunk)
      }
    }

    override protected def writeFromWindow(chunk: Int): Unit = {
      val readOffI  = writeOff.toInt
      val zeroStart = min(chunk, max(0, -startNeg - readOffI))
      if (zeroStart > 0) {
        tpe.clear(hOut.array, hOut.offset, zeroStart)
        hOut.advance(zeroStart)
      }
      val winOff1   = readOffI + zeroStart + startNeg
      val chunk1    = chunk - zeroStart
      val chunk2    = /*if (win == null) 0 else*/ min(chunk1, max(0, _winBufSize - winOff1))
      if (chunk2 > 0) {
        hOut.nextN(winBuf, winOff1, chunk2)
      }

      val zeroStop  = chunk - (chunk2 + zeroStart) // - chunk1
      if (zeroStop > 0) {
        tpe.clear(hOut.array, hOut.offset, zeroStop)
        hOut.advance(zeroStop)
      }
    }
  }
}