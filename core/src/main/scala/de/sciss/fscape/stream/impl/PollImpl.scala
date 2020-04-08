/*
 *  PollImpl.scala
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
package impl

import akka.stream.Inlet
import de.sciss.fscape.stream.impl.shapes.SinkShape2

/** Common building block for `Poll` and `Progress`. */
abstract class PollImpl[A, E <: BufElem[A]](name: String, layer: Layer, shape: SinkShape2[E, BufI])
                                           (implicit control: Control, tpe: StreamType[A, E])
  extends Handlers[SinkShape2[E, BufI]](name, layer, shape) {

  private[this] val hIn   = Handlers.InMain[A, E] (this, shape.in0)
  private[this] val hGate = Handlers.InIAux       (this, shape.in1)()

  // ---- abstract ----

  protected def trigger(buf: Array[A], off: Int): Unit

  // ---- impl ----

  protected def onDone(inlet: Inlet[_]): Unit =
    completeStage()

  final def process(): Unit = {
    val rem = math.min(hIn.available, hGate.available)
    if (rem == 0) return

    logStream(s"process() $this")

    val in      = hIn.array
    var inOff   = hIn.offset
    val stop    = inOff + rem
    while (inOff < stop) {
      val gate = hGate.next() > 0
      if (gate) {
        trigger(in, inOff)
      }
      inOff += 1
    }
    hIn.advance(rem)

    if (hIn.isDone) {
      logStream(s"completeStage() $this")
      completeStage()
    }
  }
}