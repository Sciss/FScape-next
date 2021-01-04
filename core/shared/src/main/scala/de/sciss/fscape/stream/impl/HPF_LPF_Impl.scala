/*
 *  HPF_LPF_Impl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.{FanInShape2, Inlet}
import de.sciss.fscape.Util.sqrt2
import de.sciss.fscape.stream.{BufD, Control, Layer}

import scala.annotation.tailrec
import scala.math.{Pi, min, tan}

final class HPF_LPF_Impl(shape: FanInShape2[BufD, BufD, BufD], layer: Layer, name: String, isHPF: Boolean)
                        (implicit ctrl: Control)
  extends Handlers(name, layer, shape) {

  private[this] val hIn   = Handlers.InDMain  (this, shape.in0)
  private[this] val hFreq = Handlers.InDAux   (this, shape.in1)()
  private[this] val hOut  = Handlers.OutDMain (this, shape.out)

  private[this] var freqN = Double.NaN

  private[this] var b0    = 0.0
  private[this] var a1    = 0.0
  private[this] var a2    = 0.0
  private[this] var y1    = 0.0
  private[this] var y2    = 0.0

  private[this] final val a1f = if (isHPF) -2.0 else +2.0

  protected def onDone(inlet: Inlet[_]): Unit =
    if (hOut.flush()) completeStage()

  @tailrec
  protected def process(): Unit = {
    val rem = min(hIn.available, min(hOut.available, hFreq.available))
    if (rem == 0) return

    val in      = hIn.array
    val out     = hOut.array

    var _freqN  = freqN
    var _b0     = b0
    var _a1     = a1
    var _a2     = a2
    var _y1     = y1
    var _y2     = y2

    var inOffI  = hIn .offset
    var outOffI = hOut.offset
    val stop0   = inOffI + rem
    while (inOffI < stop0) {
      val _freqN1 = hFreq.next()
      if (_freqN1 != _freqN) {
        _freqN      = _freqN1
        val freqR   = _freqN * Pi
        val c0      = tan(freqR)
        val c       = if (isHPF) c0 else 1.0 / c0
        val cSqr    = c * c
        val sqrt2c  = c * sqrt2
        _b0         = 1.0 / (1.0 + sqrt2c + cSqr)
        _a1         = -a1f * (1.0 - cSqr) * _b0
        _a2         = -(1.0 - sqrt2c + cSqr) * _b0
      }
      val x0        = in(inOffI)
      val y0        = _b0 * x0 + _a1 * _y1 + _a2 * _y2
      out(outOffI)  = _b0 * (y0 + a1f * _y1 + _y2)
      inOffI       += 1
      outOffI      += 1
      _y2           = _y1
      _y1           = y0
    }
    hIn .advance(rem)
    hOut.advance(rem)

    freqN = _freqN
    b0    = _b0
    a1    = _a1
    a2    = _a2
    y1    = _y1
    y2    = _y2

    if (hIn.isDone) {
      if (hOut.flush()) completeStage()
      return
    }

    process()
  }
}