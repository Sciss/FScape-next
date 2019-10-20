/*
 *  Frames.scala
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

import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1LImpl, NodeImpl, StageImpl}

object Frames {
  def apply(in: OutA)(implicit b: Builder): OutL = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Frames"

  private type Shape = FlowShape[BufLike, BufL]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FlowShape(
      in  = InA (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterChunkImpl[BufLike, BufL, Shape]
      with FilterIn1LImpl[BufLike] {

    private[this] var framesRead = 0L

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val bufOut  = bufOut0
      val arr     = bufOut.buf
      var i       = outOff
      val stop    = i + len
      var j       = framesRead
      while (i < stop) {
        j += 1
        arr(i) = j
        i += 1
      }
      framesRead  = j
    }
  }
}