/*
 *  FilterInAAOut1A.scala
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

import akka.stream.{FlowShape, Inlet, Outlet, Shape}
import de.sciss.fscape.stream.impl.Handlers
import de.sciss.fscape.stream.impl.Handlers.{InMain, OutMain}
import de.sciss.fscape.stream.{BufElem, Control, Layer, StreamType}

import scala.annotation.tailrec
import scala.math.min

/** Building block for a filter with one hot inlet.
  * Implementing classes have to provide the core loop `run`, and
  * calculate the number of frames available for all but the hot inlet through `auxAvailable`.
  */
abstract class FilterInAOutB[A, E <: BufElem[A], B, F <: BufElem[B], S <: Shape](name: String, layer: Layer,
                                                                                 shape: S)(inlet: Inlet[E], outlet: Outlet[F])
                                                                                (implicit ctrl: Control,
                                                                     aTpe: StreamType[A, E],
                                                                     bTpe: StreamType[B, F])
  extends Handlers(name, layer, shape) {

  private[this] val hIn : InMain [A, E] = InMain [A, E](this, inlet )
  private[this] val hOut: OutMain[B, F] = OutMain[B, F](this, outlet)

  protected def onDone(inlet: Inlet[_]): Unit =
    if (hOut.flush()) completeStage()

  protected def run(in: Array[A], inOff: Int, out: Array[B], outOff: Int, n: Int): Unit

  protected def auxInAvailable: Int

  @tailrec
  final protected def process(): Unit = {
    val remIO = min(hIn.available, hOut.available)
    if (remIO == 0) return
    val rem   = min(remIO, auxInAvailable)
    if (rem   == 0) return

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

/** Building block for a one-inlet in/out filter with `FlowShape`.
  * Implementing classes have to provide the core loop `run`.
  */
abstract class FilterIn1Out1[A, E <: BufElem[A], B, F <: BufElem[B]](name: String, layer: Layer,
                                                                     shape: FlowShape[E, F])
                                                                    (implicit ctrl: Control,
                                                                     aTpe: StreamType[A, E],
                                                                     bTpe: StreamType[B, F])
  extends FilterInAOutB[A, E, B, F, FlowShape[E, F]](name, layer, shape)(shape.in, shape.out) {

  protected final def auxInAvailable: Int = Int.MaxValue
}