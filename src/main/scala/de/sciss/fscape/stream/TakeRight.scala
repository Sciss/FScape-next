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

import akka.stream.{Attributes, FanInShape2}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import de.sciss.fscape.graph.ConstantI
import de.sciss.fscape.stream.impl.{FilterIn2Impl, WindowedFilterLogicImpl}

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
      with FilterIn2Impl[BufD, BufI, BufD]
      with WindowedFilterLogicImpl[BufD, BufD, FanInShape2[BufD, BufI, BufD]] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    private[this] var len     : Int           = -1 // negative value triggers init
    private[this] var bufSize : Int           = _
    private[this] var bufWin  : Array[Double] = _
    private[this] var bufOff  : Int           = 0 // cyclical pointer

    protected def startNextWindow(inOff: Int): Int = {
      if (len < 0) {
        assert(inOff == 0)
        len       = math.max(1, bufIn1.buf(inOff))
        // theoretically we could go with `bufSize == len`,
        // but increasing up to `ctrl.bufSize` should make
        // this play nicely with the context and perform faster.
        bufSize   = math.max(len, ctrl.bufSize)
        bufWin    = new Array[Double](bufSize)
      }
      assert(bufSize > 0)
      bufSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = {
      // it's important to treat `bufWin` as cyclical, we don't want to "lose" previous content;
      // we track that using `bufOff`, and thus `writeToWinOff` is not used.
      val chunk1  = math.min(chunk, bufSize - bufOff)
      var inOff1  = inOff
      if (chunk1 > 0) {
        Util.copy(bufIn0.buf, inOff1, bufWin, bufOff, chunk1)
        bufOff += chunk1
        inOff1 += chunk1
      }
      val chunk2 = chunk - chunk1
      if (chunk2 > 0) {
        Util.copy(bufIn0.buf, inOff1, bufWin, bufOff, chunk2)
        bufOff += chunk2
      }
    }

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = if (flush) len else 0

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
      val off0    = (bufOff - len + readFromWinOff + bufSize) % bufSize
      val chunk1  = math.min(bufSize - off0, chunk)
      Util.copy(bufWin, off0, bufOut.buf, outOff, chunk1)
      val chunk2  = chunk - chunk1
      if (chunk2 > 0) {
        Util.copy(bufWin, off0 + chunk1, bufOut.buf, outOff + chunk1, chunk2)
      }
    }
  }
}