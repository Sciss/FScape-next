/*
 *  Sheet1D.scala
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

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.shapes.SinkShape2
import de.sciss.fscape.stream.impl.{NodeImpl, Plot1DLogic, StageImpl}
import de.sciss.swingplus.ListView
import javax.swing.table.{AbstractTableModel, DefaultTableColumnModel, TableColumn}

import scala.swing.ScrollPane.BarPolicy
import scala.swing.Table.{AutoResizeMode, ElementMode}
import scala.swing.{Component, ScrollPane, Swing, Table}

object Sheet1D {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, label: String)
                               (implicit b: Builder, tpe: StreamType[A, E]): Unit = {
    val stage0  = new Stage[A, E](layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
  }

  private final val name = "Sheet1D"

  private type Shp[E] = SinkShape2[E, BufI]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, label: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = SinkShape2(
      in0 = Inlet[E](s"$name.in"  ),
      in1 = InI     (s"$name.size")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic[A, E](shape = shape, layer = layer, label = label)
  }

  private final class PlotData[A](
                                /*val hName: String, val hUnits: String,*/ val hData: Array[String],
                                /*val vName: String, val vUnits: String,*/ val vData: Array[String],
//                                /*val mName: String, val mUnits: String,*/ val mData: Array[Array[A]],
                                /*val mName: String, val mUnits: String,*/ val mData: Array[A],
                                val is1D: Boolean
                              )

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, label: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Plot1DLogic[A, E](name, shape, layer, label) {

    private[this] var _plotData = new PlotData[A](
      /*"", "",*/ new Array(0), /*"", "",*/ new Array(0), /*"", "",*/ null /*new Array(0)*/, is1D = false)

    private[this] object mTable extends AbstractTableModel {
      def getRowCount   : Int = _plotData.vData.length
      def getColumnCount: Int = if (_plotData.is1D) 1 else _plotData.hData.length

      def getValueAt(rowIdx: Int, colIdx: Int): AnyRef = {
        val f = _plotData.mData(rowIdx) // if (_plotData.is1D) _plotData.mData(0)(rowIdx) else _plotData.mData(rowIdx)(colIdx)
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
    }

    // called on EDT
    private def updateData(data: PlotData[A], hasGUI: Boolean): Unit = {
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
      if (!hasGUI) {
        //        val plot      = chart.getSheet.asInstanceOf[XYSheet]
        //        val xAxis     = plot.getDomainAxis
        //        val renderer  = plot.getRenderer
        frame.open()
      }
    }

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

    protected lazy val panel: Component = {
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

    protected def processWindow(data: Array[A], num: Int, framesRead: Long, hasGUI: Boolean): Unit = {
      val vData   = Array.tabulate(num)(_.toString)
      val hData   = Array.tabulate(1)(_.toString)
      val mData0  = tpe.newArray(num)
      System.arraycopy(data, 0, mData0, 0, num)
      val _data = new PlotData(
        hData = hData,
        vData = vData,
        mData = mData0, // Array[Array[A]](mData0),
        is1D  = true
      )
      Swing.onEDT {
        updateData(_data, hasGUI = hasGUI)
      }
    }
  }
}