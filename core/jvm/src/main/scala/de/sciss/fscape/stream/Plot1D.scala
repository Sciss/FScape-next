/*
 *  Plot1D.scala
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

import java.awt.Color

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.chart.module.Charting
import de.sciss.fscape.stream.impl.shapes.SinkShape2
import de.sciss.fscape.stream.impl.{NodeImpl, Plot1DLogic, StageImpl}
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.{ChartFactory, ChartPanel}
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}

import scala.swing.{Component, Swing}

object Plot1D {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, label: String)
                               (implicit b: Builder, tpe: StreamType[A, E]): Unit = {
    val stage0  = new Stage[A, E](layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
  }

  private final val name = "Plot1D"

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

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, label: String)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Plot1DLogic[A, E](name, shape, layer, label) {

    private[this] lazy val dataset = new XYSeriesCollection

    private[this] lazy val chart = {
      val res = ChartFactory.createXYStepChart /* createXYLineChart */(
        null, null, null, dataset,
        PlotOrientation.VERTICAL,
        false,  // legend
        false,  // tooltips
        false   // urls
      )
      // cf. https://stackoverflow.com/questions/38163136/
      val plot = res.getPlot.asInstanceOf[XYPlot]
      val axis = new NumberAxis()
      // axis.getTickUnit   ...
//      axis.setTickUnit(new NumberTickUnit(1, NumberFormat.getIntegerInstance(Locale.US)))
      plot.setDomainAxis(axis)
      res
    }

    private def updateData(series: XYSeries, pos: Long, _hasGUI: Boolean): Unit = {
      val ds = dataset
      ds.removeAllSeries()
      ds.addSeries(series)
      val n = series.getItemCount
      chart.getXYPlot.getDomainAxis.setRange(pos.toDouble, (pos + n).toDouble)

      if (!_hasGUI) {
//        val plot      = chart.getPlot.asInstanceOf[XYPlot]
//        val xAxis     = plot.getDomainAxis
//        val renderer  = plot.getRenderer
        frame.open()
      }
//      if (ended) endedGUI()
    }

    private[this] lazy val panelJ = {
      val ch        = chart
      val res       = new ChartPanel(ch, false)
      res.setBackground(Color.white)
      val plot      = ch.getPlot.asInstanceOf[XYPlot]
      val renderer  = plot.getRenderer
      renderer.setSeriesPaint (0, Color.black) // if (i == 0) Color.black else Color.red)
      // renderer.setSeriesStroke(0, strokes(i % strokes.size))
      // renderer.setSeriesShape (0, shapes (i % shapes .size))
      plot.setBackgroundPaint    (Color.white)
      plot.setDomainGridlinePaint(Color.gray )
      plot.setRangeGridlinePaint (Color.gray )
      res
    }

    protected lazy val panel: Component = Component.wrap(panelJ)

    protected def processWindow(data: Array[A], num: Int, framesRead: Long, hasGUI: Boolean): Unit = {
      import Charting._
      val series = new XYSeries(name, false /* autoSort */, false /* allowDuplicateXValues */)
      var i = 0
      var f = framesRead
      while (i < num) {
        series.add(f.toDouble, data(i).asInstanceOf[Number])
        i += 1
        f += 1
      }
      Swing.onEDT {
        updateData(series, pos = framesRead, _hasGUI = hasGUI)
      }
    }
  }
}