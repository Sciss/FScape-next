/*
 *  FilterIn1AOut1A.scala
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

package de.sciss.fscape.stream.impl.logic

import akka.stream.{FlowShape, Inlet}
import de.sciss.fscape.stream.impl.Handlers
import de.sciss.fscape.stream.impl.Handlers.{InMain, OutMain}
import de.sciss.fscape.stream.{BufElem, Control, Layer, StreamType}

import scala.annotation.tailrec
import scala.math.min

/** Building block for a one-inlet in/out filter.
  * Implementing classes have to provide the core loop `run`.
  */
abstract class FilterIn1Out1[A, E <: BufElem[A], B, F <: BufElem[B]](name: String, layer: Layer,
                                                                     shape: FlowShape[E, F])
                                                                    (implicit ctrl: Control,
                                                                     aTpe: StreamType[A, E],
                                                                     bTpe: StreamType[B, F])
  extends Handlers(name, layer, shape) {

  private[this] val hIn : InMain [A, E] = InMain [A, E](this, shape.in )
  private[this] val hOut: OutMain[B, F] = OutMain[B, F](this, shape.out)

  protected def onDone(inlet: Inlet[_]): Unit =
    if (hOut.flush()) completeStage()

  protected def run(in: Array[A], inOff: Int, out: Array[B], outOff: Int, n: Int): Unit

  @tailrec
  final protected def process(): Unit = {
    val rem     = min(hIn.available, hOut.available)
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
  