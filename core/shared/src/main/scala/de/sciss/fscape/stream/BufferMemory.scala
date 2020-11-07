/*
 *  BufferMemory.scala
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
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.math.{max, min}

object BufferMemory {
  def apply[A, E <: BufElem[A]](in: Outlet[E], length: OutI, lenMul: Int = 1)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer, lenMul = lenMul)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "BufferMemory"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, lenMul: Int)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet[E]  (s"$name.in"    ),
      in1 = InI       (s"$name.length"),
      out = Outlet[E] (s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic[A, E](shape, layer, lenMul = lenMul)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, lenMul: Int)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers[Shp[E]](name, layer, shape) {

    private[this] val hIn     : InMain  [A, E]  = InMain [A, E](this, shape.in0)
    private[this] val hDlyLen : InIAux          = InIAux       (this, shape.in1)(max(0, _))
    private[this] val hOut    : OutMain [A, E]  = OutMain[A, E](this, shape.out)

    private[this] var needsLen    = true
    private[this] var buf: Array[A] = _   // circular
    private[this] var bufLen      = 0
    private[this] var bufPosIn    = 0   // the base position before adding delay
    private[this] var bufPosOut   = 0
    private[this] var advance     = 0   // in-pointer ahead of out-pointer

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      if (!checkInDone()) process() // the rules change (`maxOut`)
    }

    private def checkInDone(): Boolean = {
      val res = advance == 0 && hOut.flush()
      if (res) completeStage()
      res
    }

    override protected def stopped(): Unit = {
      super.stopped()
      buf = null
    }

    protected def process(): Unit = {
      logStream.debug(s"process() $this")

      if (needsLen) {
        if (!hDlyLen.hasNext) return

        val dlyLen  = hDlyLen.next() * lenMul
        // because we always process in before out,
        // it is crucial that the buffer be _larger_ than the `dlyLen`
        bufLen      = ctrl.blockSize + dlyLen
        buf         = tpe.newArray(bufLen)
        needsLen    = false
      }

      // always enter here -- `needsLen` must be `false` now
      while (true) {
        val remIn   = hIn .available
        val remOut  = hOut.available

        // never be ahead more than `bufLen` frames
        val numIn = min(remIn, bufLen - advance)
        if (numIn > 0) {
          val chunk = min(numIn, bufLen - bufPosIn)
          hIn.nextN(buf, bufPosIn, chunk)
          val chunk2 = numIn - chunk
          if (chunk2 > 0) {
            hIn.nextN(buf, 0, chunk2)
          }
          bufPosIn  = (bufPosIn + numIn) % bufLen
          advance  += numIn
        }

        val numOut = min(remOut, advance)
        if (numOut > 0) {
          val chunk = min(numOut, bufLen - bufPosOut)
          hOut.nextN(buf, bufPosOut, chunk)
          val chunk2 = numOut - chunk
          if (chunk2 > 0) {
            hOut.nextN(buf, 0, chunk2)
          }

          bufPosOut = (bufPosOut + numOut) % bufLen
          advance  -= numOut
        }

        if (hIn.isDone && checkInDone()) return

        if (numIn == 0 && numOut == 0) return
      }
    }
  }
}