/*
 *  Biquad.scala
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

import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn6DImpl, NodeImpl, StageImpl}

object Biquad {
  def apply(in: OutD, b0: OutD, b1: OutD, b2: OutD, a1: OutD, a2: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(b0, stage.in1)
    b.connect(b1, stage.in2)
    b.connect(b2, stage.in3)
    b.connect(a1, stage.in4)
    b.connect(a2, stage.in5)
    stage.out
  }

  private final val name = "Biquad"

  private type Shp = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape6(
      in0 = InD (s"$name.in"  ),
      in1 = InD (s"$name.b0"  ),
      in2 = InD (s"$name.b1"  ),
      in3 = InD (s"$name.b2"  ),
      in4 = InD (s"$name.a1"  ),
      in5 = InD (s"$name.a2"  ),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn6DImpl [BufD, BufD, BufD, BufD, BufD, BufD]
      with FilterChunkImpl[BufD, BufD, Shp] {

    private[this] var b0  = 0.0
    private[this] var b1  = 0.0
    private[this] var b2  = 0.0
    private[this] var a1  = 0.0
    private[this] var a2  = 0.0

    private[this] var x1  = 0.0
    private[this] var x2  = 0.0
    private[this] var y1  = 0.0
    private[this] var y2  = 0.0

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val bufIn   = bufIn0.buf
      val bufB0   = if (bufIn1  == null) null else bufIn1.buf
      val stopB0  = if (bufB0   == null) 0    else bufIn1.size
      val bufB1   = if (bufIn2  == null) null else bufIn2.buf
      val stopB1  = if (bufB1   == null) 0    else bufIn2.size
      val bufB2   = if (bufIn3  == null) null else bufIn3.buf
      val stopB2  = if (bufB2   == null) 0    else bufIn3.size
      val bufA1   = if (bufIn4  == null) null else bufIn4.buf
      val stopA1  = if (bufA1   == null) 0    else bufIn4.size
      val bufA2   = if (bufIn5  == null) null else bufIn5.buf
      val stopA2  = if (bufA2   == null) 0    else bufIn5.size
      val out     = bufOut0.buf
      
      var _b0     = b0
      var _b1     = b1
      var _b2     = b2
      var _a1     = a1
      var _a2     = a2
      var _x1     = x1
      var _x2     = x2
      var _y1     = y1
      var _y2     = y2
      
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        val x0    = bufIn(inOffI)
        if (inOffI < stopB0) {
          _b0 = bufB0(inOffI)
        }
        if (inOffI < stopB1) {
          _b1 = bufB1(inOffI)
        }
        if (inOffI < stopB2) {
          _b2 = bufB2(inOffI)
        }
        if (inOffI < stopA1) {
          _a1 = bufA1(inOffI)
        }
        if (inOffI < stopA2) {
          _a2 = bufA2(inOffI)
        }
        val y0        = _b0 * x0 + _b1 * _x1 + _b2 * _x2 - _a1 * _y1 - _a2 * _y2
        out(outOffI)  = y0
        inOffI       += 1
        outOffI      += 1
        _y2           = _y1
        _y1           = y0
        _x2           = _x1
        _x1           = x0
      }

      b0  = _b0
      b1  = _b1
      b2  = _b2
      a1  = _a1
      a2  = _a2
      x1  = _x1
      x2  = _x2
      y1  = _y1
      y2  = _y2
    }
  }
}