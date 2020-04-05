/*
 *  LPF.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.deprecated.{FilterChunkImpl, FilterIn2DImpl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object LPF {
  def apply(in: OutD, freqN: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(freqN , stage.in1)
    stage.out
  }

  private final val name = "LPF"

  private type Shp = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.in"    ),
      in1 = InD (s"$name.freqN" ),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final val sqrt2 = math.sqrt(2)

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn2DImpl [BufD, BufD]
      with FilterChunkImpl[BufD, BufD, Shp] {

    private[this] var freqN = Double.NaN

    private[this] var b0    = 0.0
    private[this] var a1    = 0.0
    private[this] var a2    = 0.0
    private[this] var y1    = 0.0
    private[this] var y2    = 0.0

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val bufIn     = bufIn0.buf
      val bufFreq   = if (bufIn1  == null) null else bufIn1.buf
      val stopFreq  = if (bufFreq == null) 0    else bufIn1.size
      val out       = bufOut0.buf

      var _freqN  = freqN
      var _b0     = b0
      var _a1     = a1
      var _a2     = a2
      var _y1     = y1
      var _y2     = y2

      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        val x0 = bufIn(inOffI)
        if (inOffI < stopFreq) {
          val _freqN1 = bufFreq(inOffI)
          if (_freqN1 != _freqN) {
            _freqN      = _freqN1
            val freqR   = _freqN * Math.PI
            val c       = 1.0 / Math.tan(freqR)
            val cSqr    = c * c
            val sqrt2c  = c * sqrt2
            _b0         = 1.0 / (1.0 + sqrt2c + cSqr)
            _a1         = -2.0 * (1.0 - cSqr) * _b0
            _a2         = -(1.0 - sqrt2c + cSqr) * _b0
          }
        }
        val y0        = _b0 * x0 + _a1 * _y1 + _a2 * _y2
        out(outOffI)  = _b0 * (y0 + 2.0 * _y1 + _y2)
        inOffI       += 1
        outOffI      += 1
        _y2           = _y1
        _y1           = y0
      }

      freqN = _freqN
      b0    = _b0
      a1    = _a1
      a2    = _a2
      y1    = _y1
      y2    = _y2
    }
  }
}