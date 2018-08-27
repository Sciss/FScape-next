/*
 *  Drop.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.graph.ConstantL
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn2DImpl, NodeImpl, StageImpl}

object Drop {
  def tail(in: OutD)(implicit b: Builder): OutD = {
    val length = ConstantL(1).toLong
    apply(in = in, length = length)
  }

  def apply(in: OutD, length: OutL)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "Drop"

  private type Shape = FanInShape2[BufD, BufL, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"    ),
      in1 = InL (s"$name.length"),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with ChunkImpl[Shape]
      with FilterIn2DImpl[BufD, BufL] {

    private[this] var dropRemain    = -1L
    private[this] var init          = true

    protected def processChunk(): Boolean = {
      val len = math.min(inRemain, outRemain)
      val res = len > 0
      if (res) {
        if (init) {
          dropRemain = math.max(0, bufIn1.buf(0))
          init = false
        }
        val skip  = math.min(len, dropRemain).toInt
        val chunk = len - skip
        if (chunk > 0) {
          Util.copy(bufIn0.buf, inOff + skip, bufOut0.buf, outOff, chunk)
          outOff    += chunk
          outRemain -= chunk
        }
        dropRemain -= skip
        inOff      += len
        inRemain   -= len
      }
      res
    }

    protected def shouldComplete(): Boolean = inRemain == 0 && isClosed(in0) && !isAvailable(in0)
  }
}