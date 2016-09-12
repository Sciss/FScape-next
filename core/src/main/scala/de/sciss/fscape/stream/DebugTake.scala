/*
 *  DebugTake.scala
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

import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn1DImpl, NodeImpl, StageImpl}

object DebugTake {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in , stage.in)
    stage.out
  }

  private final val name = "DebugTake"

  private type Shape = FlowShape[BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with ChunkImpl[Shape]
      with FilterIn1DImpl[BufD] {

    private[this] var takeRemain    = Long.MaxValue
    private[this] var init          = true

    //    protected def shouldComplete(): Boolean = (takeRemain == 0) || (inRemain == 0 && isClosed(in0))

    protected def shouldComplete(): Boolean =
      (takeRemain == 0) || (inRemain == 0 && {
        val res = isClosed(in0)
        if (res) assert(!isAvailable(in0))  // HHH
        res
      })

    protected def processChunk(): Boolean = {
      val len = math.min(inRemain, outRemain)
      val res = len > 0
      if (res) {
        if (init) {
          takeRemain = 3000 // math.max(0, bufIn1.buf(0))
          init = false
        }
        val chunk = math.min(len, takeRemain).toInt
        if (chunk > 0) {
          Util.copy(bufIn0.buf, inOff, bufOut0.buf, outOff, chunk)
          takeRemain -= chunk
          outOff     += chunk
          outRemain  -= chunk
        }
        inOff    += len
        inRemain -= len
      }
      res
    }
  }
}