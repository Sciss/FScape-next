/*
 *  DC.scala
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
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn1DImpl, StageImpl, NodeImpl}

// XXX TODO --- support OutA
object DC {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "DC"

  private type Shape = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with GenChunkImpl[BufD, BufD, Shape]
      with GenIn1DImpl[BufD] {

    private[this] var _init = true
    private[this] var value   : Double = _

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      if (_init) {
        value = bufIn0.buf(inOff)
        _init = false
      }

      // println(s"DC.fill($value, $chunk) -> $bufOut")

      Util.fill(bufOut0.buf, outOff, chunk, value)
    }
  }
}