/*
 *  WhiteNoise.scala
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

import akka.stream.{Attributes, SourceShape}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn0DImpl, StageImpl, NodeImpl}

import scala.util.Random

object WhiteNoise {
  def apply()(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "WhiteNoise"

  private type Shape = SourceShape[BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {

    val shape = new SourceShape(
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with GenChunkImpl[BufD, BufD, Shape]
      with GenIn0DImpl {

    private[this] val rnd: Random = ctrl.mkRandom()

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      val buf   = bufOut0.buf
      var off   = outOff
      val stop  = off + chunk
      while (off < stop) {
        buf(off) = rnd.nextDouble() * 2 - 1
        off += 1
      }
    }
  }
}