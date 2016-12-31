/*
 *  MatrixInMatrix.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape8}
import de.sciss.fscape.stream.impl.{DemandFilterIn8D, DemandFilterLogic, DemandWindowedLogic, NodeImpl, StageImpl}

import scala.annotation.tailrec

object MatrixInMatrix {
  def apply(in: OutD, rowsOuter: OutI, columnsOuter: OutI, rowsInner: OutI, columnsInner: OutI,
            rowStep: OutI, columnStep: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in          , stage.in0)
    b.connect(rowsOuter   , stage.in1)
    b.connect(columnsOuter, stage.in2)
    b.connect(rowsInner   , stage.in3)
    b.connect(columnsInner, stage.in4)
    b.connect(rowStep     , stage.in5)
    b.connect(columnStep  , stage.in6)
    b.connect(mode        , stage.in7)
    stage.out
  }

  private final val name = "MatrixInMatrix"

  private type Shape = FanInShape8[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
      in0 = InD (s"$name.in"          ),
      in1 = InI (s"$name.rowsOuter"   ),
      in2 = InI (s"$name.columnsOuter"),
      in3 = InI (s"$name.rowsInner"   ),
      in4 = InI (s"$name.columnsInner"),
      in5 = InI (s"$name.rowStep"     ),
      in6 = InI (s"$name.columnStep"  ),
      in7 = InI (s"$name.mode"        ),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with DemandFilterLogic[BufD, Shape]
      with DemandFilterIn8D[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI] {

    private[this] var rowsOuter   : Int  = _
    private[this] var columnsOuter: Int  = _
    private[this] var rowsInner   : Int  = _
    private[this] var columnsInner: Int  = _
    private[this] var rowStep     : Int  = _
    private[this] var columnStep  : Int  = _
    private[this] var mode        : Int  = _
    private[this] var sizeInner   : Int  = _
    private[this] var numColSteps : Int  = _
    private[this] var numRowSteps : Int  = _

    private[this] var sizeOuter = 0
    private[this] var winBuf  : Array[Double] = _

    protected def startNextWindow(): Long = {
      val inOff = auxInOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        rowsOuter = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        columnsOuter = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        rowsInner = math.min(math.max(1, bufIn3.buf(inOff)), rowsOuter)
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        columnsInner = math.min(math.max(1, bufIn4.buf(inOff)), columnsOuter)
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        rowStep = math.max(1, bufIn5.buf(inOff))
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        columnStep = math.max(1, bufIn6.buf(inOff))
      }
      if (bufIn7 != null && inOff < bufIn7.size) {
        mode = math.max(0, math.min(2, bufIn7.buf(inOff)))    // XXX TODO --- not yet used
      }
      numColSteps = columnsOuter / columnStep
      numRowSteps = rowsOuter    / rowStep
      sizeInner   = rowsInner * columnsInner

      val newSizeOuter = rowsOuter * columnsOuter
      if (newSizeOuter != sizeOuter) {
        sizeOuter = newSizeOuter
        winBuf    = new Array(newSizeOuter)
      }

      sizeOuter
    }

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, mainInOff, winBuf, writeToWinOff.toInt, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val steps   = numColSteps.toLong * numRowSteps
      val frames  = steps * sizeInner
      if (frames > 0x7FFFFFFF) sys.error(s"Matrix too large - $frames frames is larger than 32bit")
      frames.toInt
    }

    @tailrec
    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val readOffI    = readFromWinOff.toInt
      val step        = readOffI / sizeInner
      val stepOff     = readOffI % sizeInner
      val _rowsIn     = rowsInner
      val _colsIn     = columnsInner
      val _rowsOut    = rowsOuter
      val _colsOut    = columnsOuter
      // we go down the columns first, as a convention. ok?
      val rowStepIdx  = step / numColSteps
      val colStepIdx  = step % numColSteps
      val rowStart    = rowStepIdx * rowStep    - _rowsIn/2
      val colStart    = colStepIdx * columnStep - _colsIn/2
      val colStop     = colStart + _colsIn
      val rowOff      = stepOff  / _colsIn
      val colOff      = stepOff  % _colsIn

      val in          = winBuf
      val out         = bufOut0.buf
      var outOffI     = outOff

      var colIdx      = colStart + colOff
      var rowIdx      = rowStart + rowOff

      val chunk1      = _colsIn - colOff + (_rowsIn - rowOff - 1) * _colsIn
      val chunk2      = math.min(chunk, chunk1)
      var rem         = chunk2
      while (rem > 0) {
        // XXX TODO --- implement modes. We wrap around here
        val colIdx1   = if (colIdx < 0) colIdx + _colsOut else if (colIdx >= _colsOut) colIdx - _colsOut else colIdx
        val rowIdx1   = if (rowIdx < 0) rowIdx + _rowsOut else if (rowIdx >= _rowsOut) rowIdx - _rowsOut else rowIdx
        // println(s"row $rowIdx1, col $colIdx1")
        // Yes, I know, we could avoid having to multiply in each iteration...
        val inIdx     = rowIdx1 * _colsOut + colIdx1
        val x         = in(inIdx)
        out(outOffI)  = x
        
        colIdx  += 1
        outOffI += 1
        rem     -= 1

        if (colIdx == colStop) {
          colIdx  = colStart
          rowIdx += 1
        }
      }
      
      if (chunk2 < chunk) 
        copyWindowToOutput(readFromWinOff = readFromWinOff + chunk2, outOff = outOff + chunk2, chunk = chunk - chunk2)
    }
  }
}