/*
 *  RunningValueImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream
package impl

import akka.stream.{FanInShape2, Inlet}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InMain, OutMain}

import scala.annotation.tailrec
import scala.math.min

final class RunningValueImpl[@specialized(Args) A, E <: BufElem[A]](name: String, layer: Layer,
                                                                    shape: FanInShape2[E, BufI, E],
                                                 neutralValue: A)(combine: (A, A) => A)
                                                (implicit control: Control, tpe: StreamType[A, E])
  extends Handlers[FanInShape2[E, BufI, E]](name, layer, shape) {

  private[this] val hIn   : InMain  [A, E]  = InMain  [A, E](this, shape.in0)
  private[this] val hOut  : OutMain [A, E]  = OutMain [A, E](this, shape.out)
  private[this] val hGate : InIAux          = InIAux        (this, shape.in1)()

  private[this] var value = neutralValue

  private def run(in: Array[A], inOff: Int, out: Array[A], outOff: Int, len: Int): Unit = {
    var i     = inOff
    var j     = outOff
    val stop  = inOff + len
    val g     = hGate
    var y     = value
    while (i < stop) {
      val x = in(i)
      y = if (g.next() > 0) {
        x
      } else {
        combine(x, y)
      }
      out(j) = y
      i += 1
      j += 1
    }
    value = y
  }

  // ---- impl ----

  protected def onDone(inlet: Inlet[_]): Unit =
    if (hOut.flush()) completeStage()

  @tailrec
  protected def process(): Unit = {
    val rem = min(hIn.available, min(hOut.available, hGate.available))
    if (rem == 0) return

    val in      = hIn .array
    val out     = hOut.array
    val inOff   = hIn .offset
    val outOff  = hOut.offset
    run(in, inOff, out, outOff, rem)
    hIn .advance(rem)
    hOut.advance(rem)

    if (hIn.isDone) {
      if (hOut.flush()) completeStage()
      return
    }

    process()
  }
}