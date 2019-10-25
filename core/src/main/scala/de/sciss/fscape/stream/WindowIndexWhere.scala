/*
 *  WindowIndexWhere.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.impl.{DemandFilterWindowedLogic, NoParamsDemandWindowLogic, NodeImpl, StageImpl}

object WindowIndexWhere {
  def apply(p: OutI, size: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(p   , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "WindowIndexWhere"

  private type Shape = FanInShape2[BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InI (s"$name.p"   ),
      in1 = InI (s"$name.size"),
      out = OutI(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandFilterWindowedLogic[Int, BufI, Shape] with NoParamsDemandWindowLogic {

    private[this] var index : Int = _

    protected def tpeSignal: StreamType[Layer, BufI] = StreamType.int

    protected def inletSignal : Inlet [BufI]  = shape.in0
    protected def inletWinSize: InI           = shape.in1
    protected def out0        : Outlet[BufI]  = shape.out

    // constructor
    {
      installMainAndWindowHandlers()
    }

    override protected def allWinParamsReady(winInSize: Int): Int = {
      index = -1
      0
    }

    override protected def clearInputTail(win: Array[Int], readOff: Int, chunk: Int): Unit = ()

    override protected def prepareWindow(win: Array[Int], winInSize: Int, inSignalDone: Boolean): Long =
      if (inSignalDone && winInSize == 0) 0 else 1

    override protected def processInput(in  : Array[Int], inOff   : Int,
                                        win : Array[Int], readOff : Int, chunk: Int): Unit =
      if (index < 0) {
        var i = inOff
        val s = i + chunk
        while (i < s) {
          if (in(i) != 0) {
            index = readOff + (i - inOff)
            return
          }
          i += 1
        }
      }

    override protected def processOutput(win: Array[Int], winInSize : Int , writeOff: Long,
                                         out: Array[Int], winOutSize: Long, outOff  : Int, chunk: Int): Unit = {
      assert(writeOff == 0 && chunk == 1)
      out(outOff) = index
    }
  }
}