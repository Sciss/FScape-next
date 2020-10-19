/*
 *  Limiter.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{abs, max, min, pow}

object Limiter {
  def apply(in: OutD, attack: OutI, release: OutI, ceiling: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in      , stage.in0)
    b.connect(attack  , stage.in1)
    b.connect(release , stage.in2)
    b.connect(ceiling , stage.in3)
    stage.out
  }

  private final val name = "Limiter"

  private type Shp = FanInShape4[BufD, BufI, BufI, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.in"      ),
      in1 = InI (s"$name.attack"  ),
      in2 = InI (s"$name.release" ),
      in3 = InD (s"$name.ceiling" ),
      out = OutD(s"$name.out"     )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hIn   = Handlers.InDMain  (this, shape.in0)
    private[this] val hAtk  = Handlers.InIAux   (this, shape.in1)(max(1, _))
    private[this] val hRls  = Handlers.InIAux   (this, shape.in2)(max(1, _))
    private[this] val hCeil = Handlers.InDAux   (this, shape.in3)(_ % 1.0)
    private[this] val hOut  = Handlers.OutDMain (this, shape.out)

    private[this] var atk60     : Int     = 1
    private[this] var rls60     : Int     = 1
    private[this] var ceiling   : Double  = 1.0
    private[this] var atkSize   : Int     = _
    private[this] var envSize   : Int     = _

    private[this] var init          = true

    private[this] var envBuf  : Array[Double] = _
    private[this] var gainBuf : Array[Double] = _

    private[this] var gainReadOff   : Int = _
    private[this] var gainWriteOff  : Int = _
    private[this] var outSkip       : Int = _

    private[this] var framesRead    : Long = 0L
    private[this] var framesWritten : Long = 0L

    protected override def stopped(): Unit = {
      super.stopped()
      envBuf  = null
      gainBuf = null
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    private def tryInit(): Boolean = {
      val ok = hAtk.hasNext && hRls.hasNext && hCeil.hasNext
      if (ok) {
        atk60   = hAtk  .next()
        rls60   = hRls  .next()
        ceiling = hCeil .next()

        val _atkSize  = (atk60 * 2.5).toInt
        atkSize       = _atkSize
        val rlsSize   = (rls60 * 2.5).toInt
        val atkCoef   = pow(1.0e-3, 1.0 / atk60)
        val rlsCoef   = pow(1.0e-3, 1.0 / rls60)

        val _envSize    = _atkSize + rlsSize
        envSize         = _envSize
        val _env        = new Array[Double](_envSize)
        _env(_atkSize) = 1.0
        var i = 1
        while (i < _atkSize) {
          _env(_atkSize - i) = pow(atkCoef, i)
          i += 1
        }
        i = 1
        while (i < rlsSize) {
          _env(_atkSize + i) = pow(rlsCoef, i)
          i += 1
        }
        envBuf = _env

        gainBuf = new Array[Double](_envSize)
        Util.fill(gainBuf, 0, _envSize, 1.0)

        gainReadOff   = _atkSize
        gainWriteOff  = 0
        outSkip       = _atkSize
      }
      ok
    }

    @tailrec
    def process(): Unit = {
      if (init) {
        if (!tryInit()) return
        init = false
      }

      val chunk0 = min(hIn.available, hOut.available)
      if (chunk0 > 0) {
        processChunk(chunk0)

      } else if (hIn.isDone) {
        val chunk1 = min(framesRead - framesWritten, hOut.available).toInt
        if (chunk1 == 0) return

        processChunk(chunk1)
        if (framesRead == framesWritten) {
          if (hOut.flush()) completeStage()
          return
        }

      } else {
        return
      }

      process()
    }

    // chunk is guaranteed to be <= hOut.available
    private def processChunk(chunk: Int): Unit = {
      var inRem     = hIn .available
      val in        = if (inRem > 0) hIn.array  else null
      val inOff0    = if (inRem > 0) hIn.offset else 0
      var inOff     = inOff0
      val out       = hOut.array
      val outOff0   = hOut.offset
      var outOff    = outOff0

      val _env      = envBuf
      val _gain     = gainBuf
      var _gainRd   = gainReadOff
      var _gainWr   = gainWriteOff
      val _ceil     = ceiling
      val _atkSize  = atkSize
      val _envSize  = envSize
      var _outSkip  = outSkip
      var i = 0
      while (i < chunk) {
        if (_outSkip == 0) {
          out(outOff) = _gain(_gainWr)
          outOff   += 1
        } else {
          _outSkip -= 1
        }

        val m = if (inRem > 0) {
          val res = abs(in(inOff))
          inOff  += 1
          inRem  -= 1
          res
        } else {
          0.0
        }

        val amp = m * _gain(_gainRd)
        if (amp > _ceil) {
          // lin-lin:
          //   (in - inLow) / (inHigh - inLow) * (outHigh - outLow) + outLow
          // thus env.linlin(0, 1, 1.0, ceil/amp0) becomes
          //   (env - 0) / (1 - 0) * (ceil/amp0 - 1) + 1
          // = env * (ceil/amp0 - 1) + 1
          //
          // E.g. if we're overshooting by 3 dB,
          // then ceil/amp0 = 0.7. At peak we'll multiply by -0.3 + 1.0 = 0.7
          val mul = _ceil / amp - 1.0
          var j = 0
          var k = _gainRd - _atkSize
          if (k < 0) k += _envSize
          while (j < _envSize) {
            _gain(k) *= _env(j) * mul + 1.0
            j += 1
            k += 1
            if (k == _envSize) k = 0
          }
        }

        var _lap = _gainRd - _atkSize
        if (_lap < 0) _lap += _envSize
        _gain(_lap) = 1.0

        i       += 1
        _gainRd += 1
        _gainWr += 1
        if (_gainRd == _envSize) _gainRd = 0
        if (_gainWr == _envSize) _gainWr = 0

      }

      val aIn   = inOff  - inOff0
      val aOut  = outOff - outOff0
      if (aIn > 0) {
        framesRead += aIn
        hIn.advance(aIn)
      }
      if (aOut > 0) {
        framesWritten += aOut
        hOut.advance(aOut)
      }

      // println(s"read $framesRead, written $framesWritten")

      outSkip       = _outSkip
      gainReadOff   = _gainRd
      gainWriteOff  = _gainWr
    }
  }
}