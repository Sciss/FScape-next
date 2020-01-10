/*
 *  ReduceWindow.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{DemandWindowedLogic, NoParamsDemandWindowLogic, NodeImpl, StageImpl}

object ReduceWindow {
  import graph.BinaryOp.Op

  def apply(in: OutD, size: OutI, op: Op)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer, op)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "ReduceWindow"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer, op)
  }

  private final class Logic(shape: Shape, layer: Layer, op: Op)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandWindowedLogic[Double, BufD, Double, BufD, Shape] with NoParamsDemandWindowLogic {

    private[this] var value: Double  = _

    protected def inletSignal : Inlet [BufD]  = shape.in0
    protected def inletWinSize: InI           = shape.in1
    protected def out0        : Outlet[BufD]  = shape.out

    // constructor
    {
      installMainAndWindowHandlers()
    }

    protected def tpeSignal: StreamType[Double, BufD] = StreamType.double

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    override protected def allWinParamsReady(winInSize: Int): Int = 0

    override protected def processInput(in  : Array[Double], inOff  : Int,
                                        win : Array[Double], readOff: Int, chunk: Int): Unit = {
      var i       = inOff
      val stop    = i + chunk
      var _value  = value
      if (readOff == 0 && chunk > 0) {
        _value = in(inOff)
        i += 1
      }
      while (i < stop) {
        val v   = in(i)
        _value  = op(_value, v)
        i += 1
      }
      value  = _value
    }

    override protected def prepareWindow(win: Array[Double], winInSize: Int, inSignalDone: Boolean): Long =
      if (inSignalDone && winInSize == 0) 0 else 1

    protected def processOutput(win: Array[Double], winInSize : Int , writeOff: Long,
                                out: Array[Double], winOutSize: Long, outOff  : Int, chunk: Int): Unit = {
      assert(writeOff == 0 && chunk == 1)
      out(outOff) = value
    }
  }
}