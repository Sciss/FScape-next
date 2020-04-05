/*
 *  Sheet1D.scala
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

import akka.stream.Attributes
import de.sciss.fscape.stream.impl.{FilterLogicImpl, NodeImpl, Sink2Impl, SinkShape2, StageImpl}
import de.sciss.swingplus.ListView
import javax.swing.table.{AbstractTableModel, DefaultTableColumnModel, TableColumn}

import scala.annotation.tailrec
import scala.swing.ScrollPane.BarPolicy
import scala.swing.Table.{AutoResizeMode, ElementMode}
import scala.swing.{Component, Frame, ScrollPane, Swing, Table}

object Sheet1D {
  def apply(in: OutD, size: OutI, label: String)(implicit b: Builder): Unit = {
    val stage0  = new Stage(layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
  }

  private final val name = "Sheet1D"

  private type Shp = SinkShape2[BufD, BufI]

  private final class Stage(layer: Layer, label: String)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = SinkShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape = shape, layer = layer, label = label)
  }

  private final class PlotData(
                                /*val hName: String, val hUnits: String,*/ val hData: Array[String],
                                /*val vName: String, val vUnits: String,*/ val vData: Array[String],
                                /*val mName: String, val mUnits: String,*/ val mData: Array[Array[Double]],
                                val is1D: Boolean
                              )

  private final class Logic(shape: Shp, layer: Layer, label: String)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterLogicImpl[BufD, Shp]
      with Sink2Impl[BufD, BufI] {

    override def toString = s"$name-L($label)"

    private[this] var winSize: Int = _
    private[this] var winBuf: Array[Double] = _

    private[this] var framesRead        = 0L

    private[this] var inOff             = 0  // regarding `bufIn`
    protected     var inRemain          = 0

    private[this] var writeToWinOff     = 0
    private[this] var writeToWinRemain  = 0
    private[this] var isNextWindow      = true

    @volatile
    private[this] var canWriteToWindow = true

    // ---- gui ----

    private[this] var _plotData = new PlotData(
      /*"", "",*/ new Array(0), /*"", "",*/ new Array(0), /*"", "",*/ new Array(0), is1D = false)

    private[this] object mTable extends AbstractTableModel {
      def getRowCount   : Int = _plotData.vData.length
      def getColumnCount: Int = if (_plotData.is1D) 1 else _plotData.hData.length

      def getValueAt(rowIdx: Int, colIdx: Int): AnyRef = {
        //        val f = if (_plotData.mData.length > rowIdx) {
        //          val row = _plotData.mData(rowIdx)
        //          if (row.length > colIdx) row(colIdx) else Float.NaN
        //        } else Float.NaN
        val f = if (_plotData.is1D) _plotData.mData(0)(rowIdx) else _plotData.mData(rowIdx)(colIdx)
        f.toString  // XXX TODO
      }
    }

    private[this] object mTableColumn extends DefaultTableColumnModel {
      def updateHeader(): Unit =
        /*if (_plotData.is1D)*/ updateHeader1D() /* else updateHeader2D()*/

      private def mkColumn(colIdx: Int, name: String): Unit = {
        val col = new TableColumn(colIdx)
        col.setHeaderValue("")
        col.setMinWidth      (80)
        col.setPreferredWidth(240) // (80)
        addColumn(col)
      }

      private def updateHeader1D(): Unit = {
        val oldNum  = getColumnCount
        val newNum  = 1
        val stop1   = math.min(oldNum, newNum)
        var colIdx  = 0
        while (colIdx < stop1) {
          val col = getColumn(colIdx)
          col.setHeaderValue("")
          colIdx += 1
        }
        while (colIdx < newNum) {
          mkColumn(colIdx, "")
          colIdx += 1
        }
        while (colIdx < oldNum) {
          val col = getColumn(newNum)
          removeColumn(col)
          colIdx += 1
        }
      }

//      private def updateHeader2D(): Unit = {
//        import DimensionIndex.{shouldUseUnitsString, unitsStringFormatter}
//        val units   = _plotData.hUnits
//        val lbUnits = shouldUseUnitsString(units)
//        val data    = _plotData.hData
//        val labels  = if (lbUnits) {
//          val fmt = unitsStringFormatter(units)
//          data.map(fmt(_))
//        } else {
//          data.map(_.toString)
//        }
//
//        val oldNum  = getColumnCount
//        val newNum  = data.length
//        val stop1   = math.min(oldNum, newNum)
//        var colIdx  = 0
//        while (colIdx < stop1) {
//          val col = getColumn(colIdx)
//          col.setHeaderValue(labels(colIdx))
//          colIdx += 1
//        }
//        while (colIdx < newNum) {
//          mkColumn(colIdx, labels(colIdx))
//          colIdx += 1
//        }
//        while (colIdx < oldNum) {
//          val col = getColumn(newNum)
//          removeColumn(col)
//          colIdx += 1
//        }
//      }
    }

    // called on EDT
    private def updateData(data: PlotData): Unit = {
      val colSizeChanged  = data.hData.length != _plotData.hData.length
      val colDataChanged  = colSizeChanged || !data.hData.sameElements(_plotData.hData)
      val rowSizeChanged  = data.vData.length != _plotData.vData.length
      val rowDataChanged  = rowSizeChanged || !data.vData.sameElements(_plotData.vData)
      _plotData = data
      if (colDataChanged) {
        mTableColumn.updateHeader()
        mTable.fireTableStructureChanged()
      } else {
        mTable.fireTableDataChanged()
      }
      if (rowDataChanged) {
        updateRows()
      }
      if (initGUI) {
        initGUI = false
        //        val plot      = chart.getSheet.asInstanceOf[XYSheet]
        //        val xAxis     = plot.getDomainAxis
        //        val renderer  = plot.getRenderer
        frame.open()
      }
    }

    private[this] var initGUI = true

    private[this] lazy val mList = ListView.Model.empty[String]

    private def updateRows(): Unit = {
//      import DimensionIndex.{shouldUseUnitsString, unitsStringFormatter}
//      val units   = _plotData.vUnits
//      val lbUnits = shouldUseUnitsString(units)
      val data    = _plotData.vData
      val labels  =
//        if (lbUnits) {
//          val fmt = unitsStringFormatter(units)
//          data.map(fmt(_))
//        } else {
          data // .map(_.toString)
//        }

      val oldNum  = mList.size
      val newNum  = data.length
      val stop1   = math.min(oldNum, newNum)
      var colIdx  = 0
      while (colIdx < stop1) {
        mList.update(colIdx, labels(colIdx))
        colIdx += 1
      }
      while (colIdx < newNum) {
        mList  += labels(colIdx)
        colIdx += 1
      }
      if (oldNum > newNum) {
        mList.remove(newNum, oldNum - newNum)
      }
    }

    private[this] lazy val panel: Component = {
      val ggTable                   = new Table
      ggTable.peer.setAutoCreateColumnsFromModel(false)
      ggTable.peer.setColumnModel(mTableColumn)
      ggTable.autoResizeMode        = AutoResizeMode.Off
      ggTable.model                 = mTable
      ggTable.selection.elementMode = ElementMode.Cell

      // cf. http://www.java2s.com/Code/Java/Swing-Components/TableRowHeaderExample.htm
      // XXX TODO -- this looks nicer:
      // http://stackoverflow.com/questions/8187639/jtable-with-titled-rows-and-columns#8187799
      val ggRows  = new ListView[String](mList)
      ggRows.fixedCellHeight  = ggTable.rowHeight
      ggRows.enabled          = false
      //        fixedCellWidth    = 160 // maxRow.toString.length * 13
      //        fixedCellHeight   =  24 // rowHeightIn
      //        visibleRowCount   =  12 // inVisRows
      //      }
      val ggScroll = new ScrollPane(ggTable)
      ggScroll.horizontalScrollBarPolicy  = BarPolicy.Always
      ggScroll.verticalScrollBarPolicy    = BarPolicy.Always
      ggScroll.rowHeaderView = Some(ggRows)

      ggScroll
    }

    private[this] lazy val frame = new Frame {
      title     = label
      contents  = panel
      pack()
      centerOnScreen()
    }

    // ---- methods ----

    //    override def launch(): Unit = {
    //      super.launch()
    //      Swing.onEDT {
    //        frame.open()
    //      }
    //    }

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    private def shouldComplete(): Boolean = inputsEnded && writeToWinOff == 0 // && readFromWinRemain == 0

    @tailrec
    def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (shouldRead) {
        inRemain    = readIns()
        inOff       = 0
        stateChange = true
      }

      if (processChunk()) stateChange = true

      if (shouldComplete()) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }

    private def startNextWindow(inOff: Int): Int = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(0, bufIn1.buf(inOff))
      }
      if (oldSize != winSize) {
        winBuf = new Array[Double](winSize)
      }
      winSize
    }

    private def copyInputToWindow(chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    private def processWindow(writeToWinOff: Int): Unit = {
      val vData   = Array.tabulate(writeToWinOff)(_.toString)
      val hData   = Array.tabulate(1)(_.toString)
      val mData0  = new Array[Double](writeToWinOff)
      System.arraycopy(winBuf, 0, mData0, 0, writeToWinOff)
      val _data = new PlotData(
        hData = hData,
        vData = vData,
        mData = Array.fill(1)(mData0),
        is1D  = true
      )
      assert(canWriteToWindow)
      canWriteToWindow = false
      Swing.onEDT {
        updateData(_data)
      }
      framesRead += writeToWinOff
    }

    private def processChunk(): Boolean = {
      var stateChange = false

      if (canWriteToWindow) {
        val flushIn0 = inputsEnded // inRemain == 0 && shouldComplete()
        if (isNextWindow && !flushIn0) {
          writeToWinRemain  = startNextWindow(inOff = inOff)
          isNextWindow      = false
          stateChange       = true
        }

        val chunk     = math.min(writeToWinRemain, inRemain)
        val flushIn   = flushIn0 && writeToWinOff > 0
        if (chunk > 0 || flushIn) {
          if (chunk > 0) {
            copyInputToWindow(chunk = chunk)
            inOff            += chunk
            inRemain         -= chunk
            writeToWinOff    += chunk
            writeToWinRemain -= chunk
            stateChange       = true
          }

          if (writeToWinRemain == 0 || flushIn) {
            processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
            writeToWinOff     = 0
            // readFromWinOff    = 0
            isNextWindow      = true
            stateChange       = true
          }
        }
      }

      stateChange
    }
  }
}