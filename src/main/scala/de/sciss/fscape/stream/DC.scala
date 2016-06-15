/*
 *  DC.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn1Impl, Out1LogicImpl, StageImpl, StageLogicImpl}

object DC {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "DC"

  private type Shape = FlowShape[BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with GenChunkImpl[BufD, BufD, Shape]
      with Out1LogicImpl[BufD, Shape]
      with GenIn1Impl[BufD, BufD] {

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    protected def in0: Inlet [BufD] = shape.in
    protected def out0: Outlet[BufD] = shape.out

    private[this] var init = true
    private[this] var value   : Double = _

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Int = {
      if (init) {
        value   = bufIn0.buf(inOff)
        init    = false
      }

      // println(s"DC.fill($value, $chunk) -> $bufOut")

      Util.fill(bufOut0.buf, outOff, chunk, value)
      chunk
    }
  }
}