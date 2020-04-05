/*
 *  FilterSeq.scala
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
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn2Impl, NodeImpl, StageImpl}

object FilterSeq {
  def apply[A, E <: BufElem[A]](in: Outlet[E], gate: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(gate, stage.in1)
    stage.out
  }

  private final val name = "FilterSeq"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in"  ),
      in1 = InI      (s"$name.gate"),
      out = Outlet[E](s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with FilterIn2Impl[E, BufI, E]
      with ChunkImpl[Shp[E]] {

    private[this] var high = false

    protected def shouldComplete(): Boolean =
      inRemain == 0 && isClosed(in0) && !isAvailable(in0)

    /** Should read and possibly update `inRemain`, `outRemain`, `inOff`, `outOff`.
      *
      * @return `true` if this method did any actual processing.
      */
    protected def processChunk(): Boolean = {
      val inRemain0   = inRemain
      var inRemainI   = inRemain0
      var outRemainI  = outRemain
      val b0          = bufIn0.buf
      val b1          = if (bufIn1 == null) null else bufIn1.buf
      val stop1       = if (b1     == null) 0    else bufIn1.size
      val out         = bufOut0.buf
      var h0          = high
      var inOffI      = inOff
      var outOffI     = outOff
      while (inRemainI > 0 && outRemainI > 0) {
        if (inOffI < stop1) h0 = b1(inOffI) > 0
        if (h0) {
          val v0 = b0(inOffI)
          out(outOffI) = v0
          outOffI     += 1
          outRemainI  -= 1
        }
        inOffI    += 1
        inRemainI -= 1
      }
      high = h0

      inOff     = inOffI
      outOff    = outOffI
      inRemain  = inRemainI
      outRemain = outRemainI
      inRemainI != inRemain0
    }

    protected def allocOutBuf0(): E = tpe.allocBuf()
  }
}