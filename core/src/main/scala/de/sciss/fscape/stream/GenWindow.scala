/*
 *  GenWindow.scala
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

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.deprecated.{DemandGenIn3D, DemandWindowedLogicOLD}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object GenWindow {
  import graph.GenWindow.{Hann, Shape => WinShape}

  def apply(size: OutL, shape: OutI, param: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0) 
    b.connect(size  , stage.in0)
    b.connect(shape , stage.in1)
    b.connect(param , stage.in2)
    stage.out
  }

  private final val name = "GenWindow"

  private type Shp = FanInShape3[BufL, BufI, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {

    val shape: Shape = new FanInShape3(
      in0 = InL (s"$name.size" ),
      in1 = InI (s"$name.shape"),
      in2 = InD (s"$name.param"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandWindowedLogicOLD[Shp]
      with DemandGenIn3D[BufL, BufI, BufD] {

    // private[this] var winBuf : Array[Double] = _
    private[this] var winSize: Long     = _
    private[this] var _shape : WinShape = Hann  // arbitrary default
    private[this] var param  : Double   = _

    protected def inputsEnded: Boolean = false         // never

    protected def startNextWindow(): Long = {
      val inOff = auxInOff
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
      winSize
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && {
      isClosed(in1) && isClosed(in2)
    })

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = ()

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      _shape.fill(winSize = winSize, winOff = readFromWinOff, buf = bufOut0.buf, bufOff = outOff,
        len = chunk, param = param)
    }

    protected def processWindow(writeToWinOff: Long): Long = writeToWinOff
  }
}