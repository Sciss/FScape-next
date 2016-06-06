/*
 *  GenWindow.scala
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
import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{GenIn3Impl, WindowedLogicImpl}

object GenWindow {
  import graph.GenWindow.{Shape, Hann}

  def apply(size: OutI, shape: OutI, param: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0) 
    b.connect(size  , stage.in0)
    b.connect(shape , stage.in1)
    b.connect(param , stage.in2)
    stage.out
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape3[BufI, BufI, BufD, BufD]] {

    val shape = new FanInShape3(
      in0 = InI ("GenWindow.size" ),
      in1 = InI ("GenWindow.shape"),
      in2 = InD ("GenWindow.param"),
      out = OutD("GenWindow.out"  )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(protected val shape: FanInShape3[BufI, BufI, BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with WindowedLogicImpl[BufD, BufD, FanInShape3[BufI, BufI, BufD, BufD]]
      with GenIn3Impl                               [BufI, BufI, BufD, BufD] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    // private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int    = _
    private[this] var _shape : Shape  = Hann  // arbitrary default
    private[this] var param  : Double = _

    protected def shouldComplete(): Boolean = false         // never

    protected def startNextWindow(inOff: Int): Int = {
//      val oldSize = winSize
      if (bufIn0 != null && inOff < bufIn0.size) {
        winSize = math.max(0, bufIn0.buf(inOff))
      }
      if (bufIn1 != null && inOff < bufIn1.size) {
        val shapeId = math.max(Shape.MinId, math.min(Shape.MaxId, bufIn1.buf(inOff)))
        if (shapeId != _shape.id) _shape = Shape(shapeId)
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        param = bufIn2.buf(inOff)
      }
//      if (winSize != oldSize) {
//        winBuf = new Array[Double](winSize)
//      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = ()

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = {
      // Util.copy(winBuf, readFromWinOff, bufOut.buf, outOff, chunk)
      _shape.fill(winSize = winSize, winOff = readFromWinOff, buf = bufOut.buf, bufOff = outOff,
        len = chunk, param = param)
    }

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = writeToWinOff
  }
}