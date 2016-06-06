/*
 *  TakeRight.scala
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
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.graph.ConstantI
import de.sciss.fscape.stream.impl.FilterIn2Impl

object TakeRight {
  def last(in: OutD)(implicit b: Builder): OutD = {
    val len = ConstantI(1).toInt
    apply(in = in, len = len)
  }

  def apply(in: OutD, len: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in , stage.in0)
    b.connect(len, stage.in1)
    stage.out
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape2[BufD, BufI, BufD]] {

    val shape = new FanInShape2(
      in0 = InD ("TakeRight.in" ),
      in1 = InI ("TakeRight.len"),
      out = OutD("TakeRight.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(protected val shape: FanInShape2[BufD, BufI, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterIn2Impl[BufD, BufI, BufD] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    private[this] var len     : Int           = _
    private[this] var bufWin  : Array[Double] = _
    private[this] var bufWritten = 0L

    private[this] var outOff            = 0
    private[this] var outRemain         = 0
    private[this] var outSent           = true

    private[this] var bufOff    : Int = _
    private[this] var bufRemain : Int = _

    private[this] var writeMode = false

    def process(): Unit =
      if (writeMode) tryWrite()
      else {
        if (canRead) {
          readIns()
          if (bufWin == null) {
            len    = math.max(1, bufIn1.buf(0))
            bufWin = new Array[Double](len)
          }
          copyInputToBuffer()
        }
        if (isClosed(in0)) {
          bufRemain   = math.min(bufWritten, len).toInt
          bufOff      = (math.max(0L, bufWritten - len) % len).toInt
          writeMode   = true
          tryWrite()
        }
      }

    private def copyInputToBuffer(): Unit = {
      val inRemain  = bufIn0.size
      val chunk     = math.min(inRemain, len)
      var inOff     = inRemain - chunk
      var bufOff    = ((bufWritten + inOff) % len).toInt
      val chunk1    = math.min(chunk, len - bufOff)
      if (chunk1 > 0) {
        // println(s"copy1($inOff / $inRemain -> $bufOff / $len -> $chunk1")
        Util.copy(bufIn0.buf, inOff, bufWin, bufOff, chunk1)
        bufOff = (bufOff + chunk1) % len
        inOff += chunk1
      }
      val chunk2 = chunk - chunk1
      if (chunk2 > 0) {
        // println(s"copy2($inOff / $inRemain -> $bufOff / $len -> $chunk2")
        Util.copy(bufIn0.buf, inOff, bufWin, bufOff, chunk2)
        bufOff = (bufOff + chunk2) % len
        // inOff += chunk2
      }
      bufWritten += inRemain
    }

    protected def tryWrite(): Unit = {
      if (outSent) {
        bufOut        = allocOutBuf()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
      }

      val chunk = math.min(bufRemain, outRemain)
      if (chunk > 0) {
        val chunk1  = math.min(len - bufOff, chunk)
        Util.copy(bufWin, bufOff, bufOut.buf, outOff, chunk1)
        bufOff  = (bufOff + chunk1) % len
        outOff += chunk1
        val chunk2  = chunk - chunk1
        if (chunk2 > 0) {
          Util.copy(bufWin, bufOff, bufOut.buf, outOff, chunk2)
          bufOff  = (bufOff + chunk2) % len
          outOff += chunk2
        }

        bufRemain -= chunk
        outRemain -= chunk
      }

      val flushOut = bufRemain == 0
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(out, bufOut)
        } else {
          bufOut.release()
        }
        bufOut      = null
        outSent     = true
      }

      if (flushOut && outSent) completeStage()
    }
  }
}