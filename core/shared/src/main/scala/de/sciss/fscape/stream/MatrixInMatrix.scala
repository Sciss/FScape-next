/*
 *  MatrixInMatrix.scala
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

import akka.stream.{Attributes, FanInShape8}
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec
import scala.math.{max, min}

object MatrixInMatrix {
  def apply(in: OutD, rowsOuter: OutI, columnsOuter: OutI, rowsInner: OutI, columnsInner: OutI,
            rowStep: OutI, columnStep: OutI, mode: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
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

  private type Shp = FanInShape8[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape8(
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

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends FilterWindowedInAOutA[Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hRowsOut    : InIAux   = InIAux   (this, shape.in1)(max(1, _))
    private[this] val hColsOut    : InIAux   = InIAux   (this, shape.in2)(max(1, _))
    private[this] val hRowsIn     : InIAux   = InIAux   (this, shape.in3)(max(1, _))
    private[this] val hColsIn     : InIAux   = InIAux   (this, shape.in4)(max(1, _))
    private[this] val hRowStep    : InIAux   = InIAux   (this, shape.in5)(max(1, _))
    private[this] val hColStep    : InIAux   = InIAux   (this, shape.in6)(max(1, _))
    private[this] val hMode       : InIAux   = InIAux   (this, shape.in7)(_.clip(0, 2))

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
    private[this] var frames    = 0L

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hRowsOut.hasNext &&
        hColsOut.hasNext &&
        hRowsIn .hasNext &&
        hColsIn .hasNext &&
        hRowStep.hasNext &&
        hColStep.hasNext &&
        hMode   .hasNext

      if (ok) {
        rowsOuter     = hRowsOut.next()
        columnsOuter  = hColsOut.next()
        rowsInner     = min(hRowsIn.next(), rowsOuter)
        columnsInner  = min(hColsIn.next(), columnsOuter)
        rowStep       = hRowStep.next()
        columnStep    = hColStep.next()
        mode          = hMode.next()  // XXX TODO --- not yet used
        numColSteps   = columnsOuter / columnStep
        numRowSteps   = rowsOuter    / rowStep
        sizeInner     = rowsInner * columnsInner

        val newSizeOuter = rowsOuter * columnsOuter
        if (newSizeOuter != sizeOuter) {
          sizeOuter = newSizeOuter
        }

        val steps   = numColSteps.toLong * numRowSteps
        if (steps > 0x7FFFFFFF) sys.error(s"Matrix too large - $steps steps is larger than 32bit")
        frames = steps * sizeInner
      }
      ok
    }

    protected def winBufSize: Int = sizeOuter

    override protected def writeWinSize: Long = frames

    protected def processWindow(): Unit = ()

    override protected def writeFromWindow(n: Int): Unit = {
      copyWindowToOutput(writeOff, chunk = n)
    }

    @tailrec
    private def copyWindowToOutput(readFromWinOff: Long, chunk: Int): Unit = {
      val step        = (readFromWinOff / sizeInner).toInt
      val stepOff     = (readFromWinOff % sizeInner).toInt
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
      val out         = hOut.array
      var outOffI     = hOut.offset

      var colIdx      = colStart + colOff
      var rowIdx      = rowStart + rowOff

      val chunk1      = _colsIn - colOff + (_rowsIn - rowOff - 1) * _colsIn
      val chunk2      = min(chunk, chunk1)
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

      hOut.advance(chunk2)

      if (chunk2 < chunk)
        copyWindowToOutput(readFromWinOff = readFromWinOff + chunk2, chunk = chunk - chunk2)
    }
  }
}