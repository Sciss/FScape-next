/*
 *  Plot1D.scala
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

import java.awt.Color

import akka.stream.Attributes
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.impl.{FilterLogicImpl, Sink2Impl, SinkShape2, StageImpl, StageLogicImpl}
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.{ChartFactory, ChartPanel}
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}

import scala.swing.{Component, Frame, Swing}
import scalax.chart.module.Charting

object Plot1D {
  def apply(in: OutD, size: OutI, label: String)(implicit b: Builder): Unit = {
    val stage0  = new Stage(label = label)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
  }

  private final val name = "Plot1D"

  private type Shape = SinkShape2[BufD, BufI]

  private final class Stage(label: String)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = SinkShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.trig")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(label = label, shape = shape)
  }

  private final class Logic(label: String, shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with FilterLogicImpl[BufD, Shape]
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

    private[this] lazy val dataset = new XYSeriesCollection

    private[this] lazy val chart = ChartFactory.createXYStepChart /* createXYLineChart */(
      null, null, null, dataset,
      PlotOrientation.VERTICAL,
      false,  // legend
      false,  // tooltips
      false   // urls
    )

    private def updateData(series: XYSeries): Unit = {
      val ds = dataset
      ds.removeAllSeries()
      ds.addSeries(series)
      if (initGUI) {
        initGUI = false
        frame.open()
      }
    }

    private[this] var initGUI = true

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
    private[this] lazy val panel = Component.wrap(panelJ)

    private[this] lazy val frame = new Frame {
      title     = label
      contents  = panel
      pack()
      centerOnScreen()
    }

    // ---- methods ----

//    override def preStart(): Unit = {
//      super.preStart()
//      Swing.onEDT {
//        frame.open()
//      }
//    }

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    private def shouldComplete(): Boolean = inputsEnded && writeToWinOff == 0 // && readFromWinRemain == 0

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
      import Charting._
      val series = new XYSeries(name, false /* autoSort */, false /* allowDuplicateXValues */)
      var i = 0
      var f = framesRead
      val b = winBuf
      while (i < writeToWinOff) {
        series.add(f, b(i))
        i += 1
        f += 1
      }
      assert(canWriteToWindow)
      canWriteToWindow = false
      Swing.onEDT {
        updateData(series)
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