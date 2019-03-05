/*
 *  Limiter.scala
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

import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.stream.impl.{DemandFilterIn4D, NodeImpl, StageImpl}

import scala.annotation.tailrec

object Limiter {
  def apply(in: OutD, attack: OutI, release: OutI, ceiling: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in      , stage.in0)
    b.connect(attack  , stage.in1)
    b.connect(release , stage.in2)
    b.connect(ceiling , stage.in3)
    stage.out
  }

  private final val name = "Limiter"

  private type Shape = FanInShape4[BufD, BufI, BufI, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"      ),
      in1 = InI (s"$name.attack"  ),
      in2 = InI (s"$name.release" ),
      in3 = InD (s"$name.ceiling" ),
      out = OutD(s"$name.out"     )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandFilterIn4D[BufD, BufI, BufI, BufD]
  {

    private[this] var atk60     : Int     = 1
    private[this] var rls60     : Int     = 1
    //    private[this] var atkSize   : Int     = _
    //    private[this] var rlsSize   : Int     = _
    private[this] var ceiling   : Double  = 1.0
    private[this] var atkSize   : Int     = _
    private[this] var envSize   : Int     = _

    private[this] var init          = true

    //    protected def shouldComplete(): Boolean =
    //      inRemain == 0 && isClosed(in0) && !isAvailable(in0)

    private[this] var envBuf  : Array[Double] = _
    private[this] var gainBuf : Array[Double] = _

    private[this] var outOff      = 0
    private[this] var outRemain   = 0
    private[this] var inOff       = 0
    private[this] var inRemain    = 0

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

    @tailrec
    def process(): Unit = {
      var stateChange = false

      if (init) {
        if (auxCanRead) {
          val auxRemain = readAuxIns()
          if (auxRemain > 0) {
            val auxInOff = 0
            if (bufIn1 != null && auxInOff < bufIn1.size) {
              atk60     = math.max(1, bufIn1.buf(auxInOff))
            }
            if (bufIn2 != null && auxInOff < bufIn2.size) {
              rls60     = math.max(1, bufIn2.buf(auxInOff))
            }
            if (bufIn3 != null && auxInOff < bufIn3.size) {
              ceiling = bufIn3.buf(auxInOff)
            }

            val _atkSize  = (atk60 * 2.5).toInt
            atkSize       = _atkSize
            val rlsSize   = (rls60 * 2.5).toInt
            val atkCoef   = Math.pow(1.0e-3, 1.0 / atk60)
            val rlsCoef   = Math.pow(1.0e-3, 1.0 / rls60)

            val _envSize    = _atkSize + rlsSize
            envSize         = _envSize
            val _env        = new Array[Double](_envSize)
            _env(_atkSize) = 1.0
            var i = 1
            while (i < _atkSize) {
              _env(_atkSize - i) = Math.pow(atkCoef, i)
              i += 1
            }
            i = 1
            while (i < rlsSize) {
              _env(_atkSize + i) = Math.pow(rlsCoef, i)
              i += 1
            }
            envBuf = _env

            gainBuf = new Array[Double](_envSize)
            Util.fill(gainBuf, 0, _envSize, 1.0)

            gainReadOff   = _atkSize
            gainWriteOff  = 0
            outSkip       = _atkSize

            init        = false
            stateChange = true
          }
        }
      } else {
        if (bufOut0 == null) {
          bufOut0     = allocOutBuf0()
          outRemain   = bufOut0.size
          outOff      = 0
          stateChange = true
        }

        val chunk0 = math.min(inRemain, outRemain)
        if (chunk0 > 0) {
          processChunk(chunk0)
          stateChange = true

        } else {
          if (inRemain == 0 && mainCanRead) {
            inRemain = readMainIns()
            inOff = 0
            stateChange = true
          } else if (outRemain == 0 && canWrite) {
            writeOuts(outOff)
            stateChange = true
          } else if (inRemain == 0 && isClosed(in0)) {
            val chunk1 = math.min(framesRead - framesWritten, outRemain).toInt
            if (chunk1 > 0) {
              processChunk(chunk1)
              stateChange = true
            }
            if (framesRead == framesWritten && canWrite) {
              writeOuts(outOff)
              completeStage()
            }
          }
        }
      }

      if (stateChange) process()
    }

    private def processChunk(chunk: Int): Unit = {
      val _in       = bufIn0  .buf
      var _inOff    = inOff
      var _inRem    = inRemain
      val _out      = bufOut0 .buf
      val _env      = envBuf
      val _gain     = gainBuf
      var _gainRd   = gainReadOff
      var _gainWr   = gainWriteOff
      var _outOff   = outOff
      var _outRem   = outRemain
      val _ceil     = ceiling
      val _atkSize  = atkSize
      val _envSize  = envSize
      var _outSkip  = outSkip
      var i = 0
      while (i < chunk) {
        if (_outSkip == 0) {
          _out(_outOff) = _gain(_gainWr)
          _outOff   += 1
          _outRem   -= 1
        } else {
          _outSkip -= 1
        }

        val m   = if (_inRem > 0) {
          val res = Math.abs(_in(_inOff))
          _inOff  += 1
          _inRem  -= 1
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

      framesRead    += _inOff  - inOff
      framesWritten += _outOff - outOff

      // println(s"read $framesRead, written $framesWritten")

      inOff         = _inOff
      inRemain      = _inRem
      outOff        = _outOff
      outRemain     = _outRem
      outSkip       = _outSkip
      gainReadOff   = _gainRd
      gainWriteOff  = _gainWr
    }
  }
}