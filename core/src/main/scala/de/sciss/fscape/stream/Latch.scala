/*
 *  Latch.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn2Impl, NodeImpl, StageImpl}

object Latch {
  def apply[A, E <: BufElem[A]](in: Outlet[E], gate: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
    stage.out
  }

  private final val name = "Latch"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet[E]  (s"$name.in"  ),
      in1 = InI       (s"$name.gate"),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      } else if (tpe.isLong) {
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      } else if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      } else {
        new Logic[A, E](shape, layer)
      }

      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                                  (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with FilterIn2Impl[E, BufI, E]
      with FilterChunkImpl[E, E, Shp[E]] {

    private[this] var high  = false
    private[this] var held  = tpe.zero

    protected def allocOutBuf0(): E = tpe.allocBuf()

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0.buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      val out     = bufOut0.buf
      var h0      = high
      var v0      = held
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        if (inOffI < stop1) h0 = b1(inOffI) > 0
        if (h0) {
          v0 = b0(inOffI)
        }
        out(outOffI) = v0
        inOffI  += 1
        outOffI += 1
      }
      high = h0
      held = v0
    }
  }
}