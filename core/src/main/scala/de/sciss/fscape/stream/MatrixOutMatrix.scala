/*
 *  MatrixOutMatrix.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.impl.{DemandFilterIn8D, DemandFilterLogic, DemandWindowedLogic, NodeImpl, StageImpl}

object MatrixOutMatrix {
  def apply(in: OutD, rowsInner: OutI, columnsInner: OutI, columnsOuter: OutI, rowOff: OutI, columnOff: OutI,
            rowNum: OutI, columnNum: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
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

  private type Shape = FanInShape8[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
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

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with DemandFilterLogic[BufD, Shape]
      with DemandFilterIn8D[BufD, BufI, BufI, BufI, BufI, BufI, BufI, BufI] {

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
    private[this] var winInBuf  : Array[Double] = _
    private[this] var winOutBuf : Array[Double] = _

    protected def startNextWindow(): Long = {
      val inOff = auxInOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        rowsInner = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        columnsInner = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        columnsOuter = math.max(1, bufIn3.buf(inOff))
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        rowOff = math.max(0, math.min(rowsInner - 1, bufIn4.buf(inOff)))
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        columnOff = math.max(0, math.min(columnsInner - 1, bufIn5.buf(inOff)))
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        rowNum = math.max(1, math.min(rowsInner - rowOff, bufIn6.buf(inOff)))
      }
      if (bufIn7 != null && inOff < bufIn7.size) {
        columnNum = math.max(1, math.min(columnsInner - columnOff, bufIn7.buf(inOff)))
      }
      numColStitch  = math.max(1, columnsOuter / columnNum)
      columnsOuter  = columnNum * numColStitch

      val newSizeIn = numColStitch * (rowsInner * columnsInner)
      if (winSizeIn != newSizeIn) {
        winSizeIn = newSizeIn
        winInBuf  = new Array(newSizeIn)
      }
      val newSizeOut = columnsOuter * rowNum
      if (winSizeOut != newSizeOut) {
        winSizeOut = newSizeOut
        winOutBuf  = new Array(newSizeOut)
      }

      newSizeIn
    }

    override protected def stopped(): Unit = {
      super.stopped()
      winInBuf  = null
      winOutBuf = null
    }

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, mainInOff, winInBuf, writeToWinOff.toInt, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val offI    = writeToWinOff.toInt
      val _bufIn  = winInBuf
      val _bufOut = winOutBuf
      if (offI < winSizeIn) {
        Util.clear(_bufIn, offI, winSizeIn - offI)
      }

//      val FOO = _bufIn.sum
//      val BAR = _bufIn.max
//      if (BAR != 0.0) {
//        println(s"AQUI. Sum $FOO, max $BAR")
//      }

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
      winSizeOut
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winOutBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)
  }
}