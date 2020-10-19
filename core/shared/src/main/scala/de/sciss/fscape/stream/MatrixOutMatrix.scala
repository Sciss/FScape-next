/*
 *  MatrixOutMatrix.scala
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

import akka.stream.{Attributes, FanInShape8}
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.math.{max, min}

object MatrixOutMatrix {
  def apply(in: OutD, rowsInner: OutI, columnsInner: OutI, columnsOuter: OutI, rowOff: OutI, columnOff: OutI,
            rowNum: OutI, columnNum: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in          , stage.in0)
    b.connect(rowsInner   , stage.in1)
    b.connect(columnsInner, stage.in2)
    b.connect(columnsOuter, stage.in3)
    b.connect(rowOff      , stage.in4)
    b.connect(columnOff   , stage.in5)
    b.connect(rowNum      , stage.in6)
    b.connect(columnNum   , stage.in7)
    stage.out
  }

  private final val name = "MatrixOutMatrix"

  private type Shp = FanInShape8[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape8(
      in0 = InD (s"$name.in"          ),
      in1 = InI (s"$name.rowsInner"   ),
      in2 = InI (s"$name.columnsInner"),
      in3 = InI (s"$name.columnsOuter"),
      in4 = InI (s"$name.rowOff"      ),
      in5 = InI (s"$name.columnOff"   ),
      in6 = InI (s"$name.rowNum"      ),
      in7 = InI (s"$name.columnNum"   ),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends FilterWindowedInAOutA[Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hRowsIn : InIAux  = InIAux(this, shape.in1)(max(1, _))
    private[this] val hColsIn : InIAux  = InIAux(this, shape.in2)(max(1, _))
    private[this] val hColsOut: InIAux  = InIAux(this, shape.in3)(max(1, _))
    private[this] val hRowOff : InIAux  = InIAux(this, shape.in4)(max(0, _))
    private[this] val hColOff : InIAux  = InIAux(this, shape.in5)(max(0, _))
    private[this] val hRowNum : InIAux  = InIAux(this, shape.in6)(max(1, _))
    private[this] val hColNum : InIAux  = InIAux(this, shape.in7)(max(1, _))

    private[this] var rowsInner   : Int  = _
    private[this] var columnsInner: Int  = _
    private[this] var columnsOuter: Int  = _
    private[this] var rowOff      : Int  = _
    private[this] var columnOff   : Int  = _
    private[this] var rowNum      : Int  = _
    private[this] var columnNum   : Int  = _
    private[this] var numColStitch: Int  = _

    private[this] var winSizeIn   = 0
    private[this] var winSizeOut  = 0
    private[this] var winOutBuf : Array[Double] = _

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hRowsIn .hasNext &&
        hColsIn .hasNext &&
        hColsOut.hasNext &&
        hRowOff .hasNext &&
        hColOff .hasNext &&
        hRowNum .hasNext &&
        hColNum .hasNext

      if (ok) {
        rowsInner     = hRowsIn .next()
        columnsInner  = hColsIn .next()
        columnsOuter  = hColsOut.next()
        rowOff        = min(rowsInner     - 1         , hRowOff.next())
        columnOff     = min(columnsInner  - 1         , hColOff.next())
        rowNum        = min(rowsInner     - rowOff    , hRowNum.next())
        columnNum     = min(columnsInner  - columnOff , hColNum.next())

        numColStitch  = max(1, columnsOuter / columnNum)
        columnsOuter  = columnNum * numColStitch

        winSizeIn     = numColStitch * (rowsInner * columnsInner)
        val newSizeOut = columnsOuter * rowNum
        if (winSizeOut != newSizeOut) {
          winSizeOut = newSizeOut
          winOutBuf  = new Array(newSizeOut)
        }
      }
      ok
    }

    protected def winBufSize: Int = winSizeIn

    override protected def writeWinSize: Long = winSizeOut

    override protected def stopped(): Unit = {
      super.stopped()
      winOutBuf = null
    }

    override protected def writeFromWindow(n: Int): Unit = {
      val offI = writeOff.toInt
      hOut.nextN(winOutBuf, offI, n)
    }

    protected def processWindow(): Unit = {
      val offI    = readOff.toInt
      val _bufIn  = winBuf
      val _bufOut = winOutBuf
      if (offI < winSizeIn) {
        Util.clear(_bufIn, offI, winSizeIn - offI)
      }

      var stitchIdx     = 0
      val _numColStitch = numColStitch
      var stitchOff     = 0
      val _rowsInner    = rowsInner
      val _colInner     = columnsInner
      val _colOuter     = columnsOuter
      val innerSize     = _rowsInner * _colInner
      val _colNum       = columnNum
      val _rowNum       = rowNum
      val offIn0        = rowOff * _colInner + columnOff

//      var SUM = 0.0
//      var MAX = 0.0

      while (stitchIdx < _numColStitch) {
        var offIn   = stitchIdx * innerSize + offIn0
        var offOut  = stitchIdx * _colNum
        var ri      = 0
        while (ri < _rowNum) {
          var ci = 0
          while (ci < _colNum) {
//            if (offOut > offIn) {
//              println(s"Oh noes, stitchIdx = $stitchIdx, ri = $ri, ci = $ci, offIn = $offIn, offOut = $offOut")
//            }
            val v = _bufIn(offIn)
            _bufOut(offOut) = v
//            SUM += v
//            if (v > MAX) MAX = v
            offIn  += 1
            offOut += 1
            ci     += 1
          }
          offIn  += _colInner - _colNum
          offOut += _colOuter - _colNum
          ri += 1
        }
        stitchIdx += 1
        stitchOff += innerSize
      }

//      if (BAR != 0.0) {
//        println(s"BLAH. Sum $SUM, max $MAX")
//      }

//      _numColStitch * _rowNum * _colNum
//      _colOuter * _rowNum
//      winSizeOut
    }
  }
}