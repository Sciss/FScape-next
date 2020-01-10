/*
 *  WindowMaxIndex.scala
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

object WindowMaxIndex {
  def apply(in: OutD, size: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "WindowMaxIndex"

  private type Shape = FanInShape2[BufD, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      out = OutI(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandWindowedLogic[Double, BufD, Int, BufI, Shape] with NoParamsDemandWindowLogic {

    private[this] var index   : Int     = _
    private[this] var maxValue: Double  = _

    protected def inletSignal : Inlet [BufD]  = shape.in0
    protected def inletWinSize: InI           = shape.in1
    protected def out0        : Outlet[BufI]  = shape.out

    // constructor
    {
      installMainAndWindowHandlers()
    }

    protected def tpeSignal: StreamType[Double, BufD] = StreamType.double

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

    override protected def allWinParamsReady(winInSize: Int): Int = {
      maxValue  = Double.NegativeInfinity
      index     = -1
      0
    }

    override protected def processInput(in  : Array[Double], inOff  : Int,
                                        win : Array[Double], readOff: Int, chunk: Int): Unit = {
      var i       = inOff
      val s       = i + chunk
      var _max    = maxValue
      var _index  = index
      val d       = readOff - inOff
      while (i < s) {
        val v = in(i)
        if (v > _max) {
          _max    = v
          _index  = i + d
        }
        i += 1
      }
      maxValue  = _max
      index     = _index
    }

    override protected def prepareWindow(win: Array[Double], winInSize: Int, inSignalDone: Boolean): Long =
      if (inSignalDone && winInSize == 0) 0 else 1

    protected def processOutput(win: Array[Double], winInSize : Int , writeOff: Long,
                                out: Array[Int]   , winOutSize: Long, outOff  : Int, chunk: Int): Unit = {
      assert(writeOff == 0 && chunk == 1)
      out(outOff) = index
    }
  }
}