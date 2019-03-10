/*
 *  DelayN.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn3DImpl, NodeImpl, StageImpl}

object DelayN {
  def apply(in: OutD, maxLength: OutI, length: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(maxLength , stage.in1)
    b.connect(length    , stage.in2)
    stage.out
  }

  private final val name = "DelayN"

  private type Shape = FanInShape3[BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.maxLength" ),
      in2 = InI (s"$name.length"    ),
      out = OutD(s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with ChunkImpl[Shape]
      with FilterIn3DImpl[BufD, BufI, BufI] {

    private[this] var maxLength     = -1
    private[this] var _init         = true
    private[this] var delayBuf: Array[Double] = _
    private[this] var bufPtr        = 0
    private[this] var delay         = 0

    protected def processChunk(): Boolean = {
      val len = math.min(inRemain, outRemain)
      val res = len > 0
      if (res) {
        if (_init) {
          maxLength = math.max(1, bufIn1.buf(0))
          delayBuf  = new Array[Double](maxLength)
          _init     = false
        }
        var inOffI  = inOff
        var outOffI = outOff
        val inArr   = bufIn0 .buf
        val outArr  = bufOut0.buf
        val dlyArr  = if (bufIn2 == null) null else bufIn2.buf
        val dlyStop = if (bufIn2 == null) 0    else bufIn2.size
        val dlyBuf  = delayBuf
        var _delay  = delay
        val _maxLen = maxLength
        val _maxLen1= _maxLen - 1
        var _bufWr  = bufPtr
        var _bufRd  = (_bufWr - _delay + _maxLen) % _maxLen
        var rem     = len
        while (rem > 0) {
          if (inOffI < dlyStop) {
            _delay  = math.max(0, math.min(dlyArr(inOffI), _maxLen1))
            _bufRd  = (_bufWr - _delay + _maxLen) % _maxLen
          }
          dlyBuf(_bufWr)  = inArr(inOffI)
          outArr(outOffI) = dlyBuf(_bufRd)
          _bufWr  += 1; if (_bufWr == _maxLen) _bufWr = 0
          _bufRd  += 1; if (_bufRd == _maxLen) _bufRd = 0
          inOffI  += 1
          outOffI += 1
          rem     -= 1
        }
        bufPtr      = _bufWr
        delay       = _delay
        inOff       = inOffI
        outOff      = outOffI
        inRemain   -= len
        outRemain  -= len
      }
      res
    }

    protected def shouldComplete(): Boolean = inRemain == 0 && isClosed(in0) && !isAvailable(in0)
  }
}