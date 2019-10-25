/*
 *  Drop.scala
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
import de.sciss.fscape.graph.ConstantL
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn2Impl, NodeImpl, StageImpl}

object Drop {
  def tail[A, E >: Null <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val length = ConstantL(1).toLong
    apply[A, E](in = in, length = length)
  }

  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], length: OutL)
                                       (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "Drop"

  private type Shape[E] = FanInShape2[E, BufL, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shape[E]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [E] (s"$name.in"    ),
      in1 = InL       (s"$name.length"),
      out = Outlet[E] (s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer)
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with ChunkImpl[Shape[E]]
      with FilterIn2Impl[E, BufL, E] {

    private[this] var dropRemain    = -1L
    private[this] var init          = true

    protected def allocOutBuf0(): E = tpe.allocBuf()

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
          System.arraycopy(bufIn0.buf, inOff + skip, bufOut0.buf, outOff, chunk)
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