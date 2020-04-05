/*
 *  WhiteNoise.scala
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

import akka.stream.{Attributes, SourceShape}
import de.sciss.fscape.stream.impl.deprecated.{GenChunkImpl, GenIn0DImpl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.util.Random

object WhiteNoise {
  def apply()(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "WhiteNoise"

  private type Shp = SourceShape[BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {

    val shape: Shape = new SourceShape(
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with GenChunkImpl[Shp]
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