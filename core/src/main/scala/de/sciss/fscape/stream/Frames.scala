/*
 *  Frames.scala
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

import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1LImpl, NodeImpl, StageImpl}

object Frames {
  def apply(in: OutA)(implicit b: Builder): OutL = apply(in, init = 1, name = nameFr)

  def apply(in: OutA, init: Int, name: String)(implicit b: Builder): OutL = {
    val stage0  = new Stage(b.layer, init = init, name = name)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val nameFr = "Frames"

  private type Shp = FlowShape[BufLike, BufL]

  private final class Stage(layer: Layer, init: Int, name: String)(implicit ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = new FlowShape(
      in  = InA (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, init = init, name = name)
  }

  private final class Logic(shape: Shp, layer: Layer, init: Int, name: String)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterChunkImpl[BufLike, BufL, Shp]
      with FilterIn1LImpl[BufLike] {

    private[this] var framesRead = init.toLong

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val bufOut  = bufOut0
      val arr     = bufOut.buf
      var i       = outOff
      val stop    = i + len
      var j       = framesRead
      while (i < stop) {
        arr(i) = j
        j += 1
        i += 1
      }
      framesRead  = j
    }
  }
}