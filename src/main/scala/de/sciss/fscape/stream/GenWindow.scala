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
import de.sciss.fscape.stream.impl.{GenIn3Impl, Out1LogicImpl, StageLogicImpl, WindowedLogicImpl}

object GenWindow {
  import graph.GenWindow.{Shape => WinShape, Hann}

  def apply(size: OutI, shape: OutI, param: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0) 
    b.connect(size  , stage.in0)
    b.connect(shape , stage.in1)
    b.connect(param , stage.in2)
    stage.out
  }

  private final val name = "GenWindow"

  private type Shape = FanInShape3[BufI, BufI, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends GraphStage[Shape] {

    override def toString = s"$name@${hashCode.toHexString}"

    val shape = new FanInShape3(
      in0 = InI (s"$name.size" ),
      in1 = InI (s"$name.shape"),
      in2 = InD (s"$name.param"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with WindowedLogicImpl[BufD, Shape]
      with Out1LogicImpl    [BufD, Shape]
      with GenIn3Impl       [BufI, BufI, BufD, BufD] {

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    // private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Int      = _
    private[this] var _shape : WinShape = Hann  // arbitrary default
    private[this] var param  : Double   = _

    protected def shouldComplete(): Boolean = false         // never

    protected def startNextWindow(inOff: Int): Int = {
//      val oldSize = winSize
      if (bufIn0 != null && inOff < bufIn0.size) {
        winSize = math.max(0, bufIn0.buf(inOff))
      }
      if (bufIn1 != null && inOff < bufIn1.size) {
        val shapeId = math.max(WinShape.MinId, math.min(WinShape.MaxId, bufIn1.buf(inOff)))
        if (shapeId != _shape.id) _shape = WinShape(shapeId)
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
      _shape.fill(winSize = winSize, winOff = readFromWinOff, buf = bufOut0.buf, bufOff = outOff,
        len = chunk, param = param)
    }

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = writeToWinOff
  }
}