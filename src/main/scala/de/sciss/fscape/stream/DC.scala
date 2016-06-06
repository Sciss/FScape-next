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

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn1Impl}

object DC {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FlowShape[BufD, BufD]] {

    val shape = new FlowShape(
      in  = InD ("DC.in" ),
      out = OutD("DC.out")
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(protected val shape: FlowShape[BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with GenChunkImpl[BufD, BufD, FlowShape[BufD, BufD]]
      with GenIn1Impl[BufD, BufD] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    protected def in0: Inlet [BufD] = shape.in
    protected def out: Outlet[BufD] = shape.out

    private[this] var init = true
    private[this] var value   : Double = _

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Int = {
      if (init) {
        value   = bufIn0.buf(inOff)
        init    = false
      }

      // println(s"DC.fill($value, $chunk) -> $bufOut")

      Util.fill(bufOut.buf, outOff, chunk, value)
      chunk
    }
  }
}
