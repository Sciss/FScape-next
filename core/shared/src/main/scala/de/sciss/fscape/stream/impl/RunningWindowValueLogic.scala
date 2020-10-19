/*
 *  RunningWindowValueLogic.scala
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

import akka.stream.FanInShape3
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA

import scala.math.max

final class RunningWindowValueLogic[@specialized(Args) A, E <: BufElem[A]](name: String, layer: Layer,
                                                                           shape: FanInShape3[E, BufI, BufI, E])
                                                                          (combine: (A, A) => A)
                                                                          (implicit ctrl: Control,
                                                                          tpe: StreamType[A, E])
  extends FilterWindowedInAOutA[A, E, FanInShape3[E, BufI, BufI, E]](name, layer, shape)(shape.in0, shape.out) {

  // ---- abstract ----

  private[this] val hSize = InIAux(this, shape.in1)(max(1, _))
  private[this] val hGate = InIAux(this, shape.in2)()

  // ---- impl ----

//  private[this] var value = neutralValue
  private[this] var gate = false

  private[this] var winSize: Int = -1

  protected def tryObtainWinParams(): Boolean = {
    val ok = hSize.hasNext && hGate.hasNext
    if (ok) {
      val oldSize = winSize
      winSize = hSize.next()
      gate    = (hGate.next() > 0) || (winSize != oldSize)
    }
    ok
  }

  protected def winBufSize: Int = winSize

  protected def processWindow(): Unit = ()

  override protected val fullLastWindow: Boolean = false

  override protected def readIntoWindow(chunk: Int): Unit = {
    val in      = hIn.array
    var inOff   = hIn.offset
    val out     = winBuf
    var outOff  = readOff.toInt
    val g       = gate
    val stop    = inOff + chunk
    while (inOff < stop) {
      val x0      = in(inOff)
      val v       = if (g) x0 else combine(out(outOff), x0)
      out(outOff) = v
      inOff  += 1
      outOff += 1
    }
    hIn.advance(chunk)
    gate = g
  }
}