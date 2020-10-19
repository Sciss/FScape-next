/*
 *  OffsetOverlapAdd.scala
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

import akka.stream.{Attributes, FanInShape5, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{max, min}

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
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(step      , stage.in2)
    b.connect(offset    , stage.in3)
    b.connect(minOffset , stage.in4)
    stage.out
  }

  private final val name = "OffsetOverlapAdd"

  private type Shp = FanInShape5[BufD, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape5(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.size"     ),
      in2 = InI (s"$name.step"     ),
      in3 = InI (s"$name.offset"   ),
      in4 = InI (s"$name.minOffset"),
      out = OutD(s"$name.out"      )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hIn     = Handlers.InDMain  (this, shape.in0)
    private[this] val hOut    = Handlers.OutDMain (this, shape.out)
    private[this] val hSize   = Handlers.InIAux   (this, shape.in1)(max(1, _))
    private[this] val hStep   = Handlers.InIAux   (this, shape.in2)(max(1, _))
    private[this] val hOff    = Handlers.InIAux   (this, shape.in3)()
    private[this] val hMinOff = Handlers.InIAux   (this, shape.in4)()

    private[this] var size     : Int  = _
    private[this] var step     : Int  = _
    private[this] var offset   : Int  = _   // this is already corrected against `minOffset`!
    private[this] var minOffset: Int  = _

    private[this] var bufSize         = 0
    private[this] var bufWin: Array[Double] = _     // circular

    private[this] var isNextWindow    = true
    private[this] var mixToBufRemain  = 0
    private[this] var mixToBufOff     = 0
    private[this] var bufWritten      = 0L
    private[this] var bufRead         = 0L
    private[this] var maxStop         = 0L

    private[this] var init            = true
    private[this] var flushed         = false

    private def shouldComplete(): Boolean = flushed && bufRead == bufWritten

    @inline
    private def canPrepareStep: Boolean = bufRead == bufWritten /*&& hIn.hasNext*/ && !flushed

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    @tailrec
    protected def process(): Unit = {
      val ok = processChunk()
      if (shouldComplete()) {
        if (hOut.flush()) completeStage()
      } else if (ok) {
        process()
      }
    }

    private def processChunk(): Boolean = {
      // println(s"processChunk(); inOff = $inOff, outOff = $outOff, inRemain = $inRemain, outRemain = $outRemain, bufRead $bufRead, bufWritten $bufWritten, inputsEnded $inputsEnded")
      var stateChange = false

      if (canPrepareStep && isNextWindow) {
        if (!tryObtainWinParams()) return false
        maxStop       = max(maxStop, bufWritten + size + offset)
        // println(s"maxStop = $maxStop")
        isNextWindow  = false
        stateChange   = true
      }

      val chunkIn = min(mixToBufRemain, hIn.available)
      if (chunkIn > 0) {
        mixInputToBuffer(chunkIn)
        stateChange = true

        if (mixToBufRemain == 0) {
          isNextWindow   = true
          bufWritten    += step
        }
      }
      else if (hIn.isDone && !flushed) {
        bufWritten  = maxStop
        flushed     = true
        stateChange = true
        // println(s"flushed = true; bufWritten = $bufWritten")
      }

      val chunkOut = min(bufWritten - bufRead, hOut.available).toInt
      if (chunkOut > 0) {
        copyBufferToOutput(chunkOut)
        stateChange = true
      }

      stateChange
    }

    private def tryObtainWinParams(): Boolean = {
      val ok =
        hSize   .hasNext &&
        hStep   .hasNext &&
        hOff    .hasNext &&
        hMinOff .hasNext

      if (ok) {
        size = hSize.next()
        step = hStep.next()
        if (init) {
          minOffset = hMinOff.next()
          init      = false
        }
        offset = max(0, hOff.next() - minOffset)

        val newBufSize = size + offset
        if (bufSize < newBufSize) {
          // cf. https://stackoverflow.com/questions/38134091/
          val oldBufSize  = bufSize
          val newBuf      = new Array[Double](newBufSize)
          if (bufWin != null) {
            val off0      = (bufRead % oldBufSize).toInt
            val off1      = (bufRead % newBufSize).toInt
            val chunk0    = min(oldBufSize - off0, newBufSize - off1)
            System.arraycopy(bufWin, off0, newBuf, off1, chunk0)
            val off2      = (off0 + chunk0) % oldBufSize
            val off3      = (off1 + chunk0) % newBufSize
            val chunk1    = min(oldBufSize - max(chunk0, off2), newBufSize - off3)
            System.arraycopy(bufWin, off2, newBuf, off3, chunk1)
            val off4      = (off2 + chunk1) % oldBufSize
            val off5      = (off3 + chunk1) % newBufSize
            val chunk2    = oldBufSize - (chunk0 + chunk1)
            System.arraycopy(bufWin, off4, newBuf, off5, chunk2)
            bufWin = newBuf
          }
          bufWin  = newBuf
          bufSize = newBufSize
        }

        mixToBufRemain  = size
        mixToBufOff     = ((bufWritten + offset) % bufSize).toInt // 'reset'
      }

      ok
    }

    private def mixIn(chunk: Int): Unit = {
      val in    = hIn.array
      val inOff = hIn.offset
      Util.add(in, inOff, bufWin, mixToBufOff, chunk)
      mixToBufOff     = (mixToBufOff + chunk) % bufSize
      mixToBufRemain -= chunk
      hIn.advance(chunk)
    }

    private def mixInputToBuffer(chunk: Int): Unit = {
      // println(s"mixInputToBuffer($chunk); inOff = $inOff, mixToBufOff = $mixToBufOff")
      val chunk1 = min(chunk, bufSize - mixToBufOff)
      mixIn(chunk1)
      val chunk2 = chunk - chunk1
      if (chunk2 > 0) {
        mixIn(chunk2)
      }
    }

    private def copyOut(bufOff: Int, chunk: Int): Unit = {
      hOut.nextN(bufWin, bufOff, chunk)
      // we "clean up after ourselves" here, because this is the easiest spot
      Util.clear(bufWin, bufOff, chunk)
      bufRead   += chunk
    }

    private def copyBufferToOutput(chunk: Int): Unit = {
      val bufOff1 = (bufRead % bufSize).toInt
      // println(s"copyBufferToOutput($chunk); outOff = $outOff, bufRead = $bufRead, bufOff1 = $bufOff1")
      val chunk1  = min(chunk, bufSize - bufOff1)
      copyOut(bufOff1, chunk1)
      val chunk2 = chunk - chunk1
      if (chunk2 > 0) {
        copyOut(0, chunk2)
      }
    }
  }
}