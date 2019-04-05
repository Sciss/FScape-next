/*
 *  PenImage.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape14, Inlet}
import de.sciss.fscape.graph.BinaryOp.Op
import de.sciss.fscape.graph.PenImage._
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers
import de.sciss.fscape.{logStream => log}
import de.sciss.numbers.IntFunctions

import scala.annotation.switch
import scala.math.{abs, min}

object PenImage {
  def apply(src: OutD, alpha: OutD, dst: OutD, width: OutI, height: OutI, x: OutD, y: OutD, next: OutI,
            rule: OutI, op: OutI, wrap: OutI,
            rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)(implicit b: Builder): OutD = {

    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(src           , stage.in0 )
    b.connect(alpha         , stage.in1 )
    b.connect(dst           , stage.in2 )
    b.connect(width         , stage.in3 )
    b.connect(height        , stage.in4 )
    b.connect(x             , stage.in5 )
    b.connect(y             , stage.in6 )
    b.connect(next          , stage.in7 )
    b.connect(rule          , stage.in8 )
    b.connect(op            , stage.in9 )
    b.connect(wrap          , stage.in10)
    b.connect(rollOff       , stage.in11)
    b.connect(kaiserBeta    , stage.in12)
    b.connect(zeroCrossings , stage.in13)
    stage.out
  }

  private final val name = "PenImage"

  private type Shape = FanInShape14[BufD, BufD, BufD, BufI, BufI, BufD, BufD, BufI, BufI, BufI, BufI, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape14(
      in0   = InD (s"$name.src"),
      in1   = InD (s"$name.alpha"),
      in2   = InD (s"$name.dst"),
      in3   = InI (s"$name.width"),
      in4   = InI (s"$name.height"),
      in5   = InD (s"$name.x"),
      in6   = InD (s"$name.y"),
      in7   = InI (s"$name.next"),
      in8   = InI (s"$name.rule"),
      in9   = InI (s"$name.op"),
      in10  = InI (s"$name.wrap"),
      in11  = InD (s"$name.rollOff"),
      in12  = InD (s"$name.kaiserBeta"),
      in13  = InI (s"$name.zeroCrossings"),
      out   = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with OutHandler { logic =>

    // structure of inputs:
    //
    // "aux per image": in3 width, in4 height, in8 rule, in9 op, in10 wrap, in11 rollOff, in12 kaiser, in13 zc
    // "during image": in0 src, in1 alpha, in2 dst, in5 x, in6 y, in7 next
    //
    // "hot": in0 src
    // "may end any time": in1 alpha, in2 dst, in5 5, in6 y, in7 next
    //
    // Timing policy:
    // - a turn to obtain all aux data
    // - then pen polling until a trigger in `next` is received
    // - pen polling distinguishes between too groups of signals:
    //   1. `dst` needs to be read in first, as it creates the "background",
    //      i.e. we need as much as `width * height` values (or truncate if
    //      `dst` ends prematurely).
    //   2. `src`, `alpha`, `x`, `y`, `next` are synchronised.

//    private[this] var auxDataOpen   = 8
    private[this] var auxDataRem    = 8
    private[this] var auxDataReady  = false

    private[this] var dstDataRem    = 1
    private[this] var dstDataReady  = false

    private[this] var penDataRem    = 5
    private[this] var penDataReady  = false

    private[this] var width         : Int     = _
    private[this] var height        : Int     = _
    private[this] var rule          : Int     = _
    private[this] var op            : Op      = _
    private[this] var wrap          : Boolean = _
    private[this] var rollOff       : Double  = _
    private[this] var kaiserBeta    : Double  = _
    private[this] var zeroCrossings : Int     = _

    private[this] val hSrc            = new PenInHandler[Double , BufD](shape.in0 )
    private[this] val hAlpha          = new PenInHandler[Double , BufD](shape.in1 )
    private[this] val hX              = new PenInHandler[Double , BufD](shape.in5 )
    private[this] val hY              = new PenInHandler[Double , BufD](shape.in6 )
    private[this] val hNext           = new PenInHandler[Int    , BufI](shape.in7 )

    private[this] val hDst            = new DstInHandler[Double , BufD](shape.in2 )

    private[this] val hWidth          = new AuxInHandler[Int    , BufI](shape.in3 )
    private[this] val hHeight         = new AuxInHandler[Int    , BufI](shape.in4 )
    private[this] val hRule           = new AuxInHandler[Int    , BufI](shape.in8 )
    private[this] val hOp             = new AuxInHandler[Int    , BufI](shape.in9 )
    private[this] val hWrap           = new AuxInHandler[Int    , BufI](shape.in10)
    private[this] val hRollOff        = new AuxInHandler[Double , BufD](shape.in11)
    private[this] val hKaiserBeta     = new AuxInHandler[Double , BufD](shape.in12)
    private[this] val hZeroCrossings  = new AuxInHandler[Int    , BufI](shape.in13)

    private[this] val penInHandlers = Array[PenInHandler[_, _]](
      hSrc, hAlpha, /*hDst, */ hX, hY, hNext
    )

    private[this] val auxInHandlers = Array[InHandlerImpl[_, _]](
      hWidth, hHeight, hRule, hOp, hWrap, hRollOff, hKaiserBeta, hZeroCrossings
    )

    private[this] var frameSize : Int = -1

    private[this] var frameBuf  : Array[Double] = _ // of frameSize

    private[this] var stage       = 0   // 0 gather aux, 1 fill dst, 2 apply pen, 3 write out
    private[this] var dstWritten  = 0   // 0 to frameSize

//    @inline
//    private def log(what: => String): Unit =
//      println(s"[log] $what")

//    @inline
//    private def log(what: => String): Unit = logStream(what)

    override protected def stopped(): Unit = {
      super.stopped()
      auxInHandlers.foreach(_.freeBuffer())
      penInHandlers.foreach(_.freeBuffer())
      frameBuf = null
      freeOutBuffer()
    }

    private def freeOutBuffer(): Unit =
      if (bufOut != null) {
        bufOut.release()
        bufOut = null
      }

    private abstract class InHandlerImpl[A, E <: BufElem[A]](in: Inlet[E])
      extends InHandler {

      private[this] var hasValue      = false
      private[this] var everHadValue  = false

      final var buf             : E   = _
      final var offset          : Int = 0
      final var mostRecent      : A   = _

      // ---- abstract ----

      protected def notifyValue(): Unit

      // ---- impl ---

      final def bufRemain: Int = if (buf == null) 0 else buf.size - offset

      override final def toString: String = in.toString //  s"$logic.$in"

      final def hasNext: Boolean =
        (buf != null) || !isClosed(in) || isAvailable(in)

      final def freeBuffer(): Unit =
        if (buf != null) {
          mostRecent = buf.buf(buf.size - 1)
          buf.release()
          buf = null.asInstanceOf[E]
        }

      final def next(): Unit = {
        hasValue = false
        if (bufRemain > 0) {
          ackValue()
        } else {
          freeBuffer()
          if (isAvailable(in)) onPush()
        }
      }

      final def takeValue(): A =
        if (buf == null) {
          mostRecent
        } else {
          val i = buf.buf(offset)
          offset += 1
          if (offset == buf.size) {
            freeBuffer()
          }
          i
        }

      final def peekValue(): A =
        if (buf == null) {
          mostRecent
        } else {
          buf.buf(offset)
        }

      final def skipValue(): Unit =
        if (buf != null) {
          offset += 1
          if (offset == buf.size) {
            freeBuffer()
          }
        }

      final def onPush(): Unit = {
        log(s"onPush() $this - $hasValue")
        if (!hasValue) {
          assert(buf == null)
          buf = grab(in)
          assert(buf.size > 0)
          offset = 0
          ackValue()
          tryPull(in)
        }
      }

      private def ackValue(): Unit = {
        hasValue      = true
        everHadValue  = true
        notifyValue()
      }

      final override def onUpstreamFinish(): Unit = {
        log(s"onUpstreamFinish() $this - $hasValue $everHadValue")
        if (!isAvailable(in)) {
          if (everHadValue) {
            if (!hasValue) ackValue()
          } else {
            super.onUpstreamFinish()
          }
        }
      }

      setHandler(in, this)
    }

    private final class AuxInHandler[A, E <: BufElem[A]](in: Inlet[E]) extends InHandlerImpl[A, E](in) {
      protected def notifyValue(): Unit = {
        auxDataRem -= 1
        if (auxDataRem == 0) {
          notifyAuxDataReady()
        }
      }
    }

    private final class PenInHandler[A, E <: BufElem[A]](in: Inlet[E]) extends InHandlerImpl[A, E](in) {
      protected def notifyValue(): Unit = {
        penDataRem -= 1
        if (penDataRem == 0) {
          notifyPenDataReady()
        }
      }
    }

    private final class DstInHandler[A, E <: BufElem[A]](in: Inlet[E]) extends InHandlerImpl[A, E](in) {
      protected def notifyValue(): Unit = {
        dstDataRem -= 1
        if (dstDataRem == 0) {
          notifyDstDataReady()
        }
      }
    }

    // ---- out handler ----

    def onPull(): Unit = {
      log(s"onPull() $logic")
      if (stage == 3) {
        processOutData()
      }
    }

    override def onDownstreamFinish(): Unit = {
      log(s"onDownstreamFinish() $logic")
      super.onDownstreamFinish()
    }

    setHandler(shape.out, this)

    // ---- stages ----

    private def requestNextAuxData(): Unit = {
      log("requestNextAuxData")
      assert (stage == 0)
      assert (!auxDataReady)
      if (auxDataRem == 0) {  // no ongoing request
        auxDataRem = auxInHandlers.count(_.hasNext)
        if (auxDataRem > 0) {
          auxInHandlers.foreach(h => if (h.hasNext) h.next())
        } else {
          notifyAuxDataReady()
        }
      }
    }

    private def requestNextDstData(): Unit = {
      log("requestNextDstData")
      assert (stage == 1)
      assert (!dstDataReady)
      if (dstDataRem == 0) {  // no ongoing request
        if (hDst.hasNext) {
          dstDataRem = 1
          hDst.next()
        } else {
          notifyDstDataReady()
        }
      }
    }

    private def requestNextPenData(): Unit = {
      log("requestNextPenData")
      assert (stage == 2)
      assert (!penDataReady)
      if (penDataRem == 0) {  // no ongoing request
        penDataRem = penInHandlers.count(_.hasNext)
        if (penDataRem > 0) {
          penInHandlers.foreach(h => if (h.hasNext) h.next())
        } else {
          notifyPenDataReady()
        }
      }
    }

    private def notifyAuxDataReady(): Unit = {
      log("notifyAuxDataReady")
      assert (!auxDataReady)
      if (stage == 0) {
        processAuxData()
      } else {
        auxDataReady = true
      }
    }

    private def notifyDstDataReady(): Unit = {
      log("notifyDstDataReady")
      assert (!dstDataReady)
      if (stage == 1) {
        processDstData()
      } else {
        dstDataReady = true
      }
    }

    private def notifyPenDataReady(): Unit = {
      log("notifyPenDataReady")
      assert (!penDataReady)
      if (stage == 2) {
        processPenData()
      } else {
        penDataReady = true
      }
    }

    private def processAuxData(): Unit = {
      log("processAuxData")
      assert (stage == 0)
      import numbers.Implicits._

      width         = hWidth        .takeValue().max(1)
      height        = hHeight       .takeValue().max(1)
      rule          = hRule         .takeValue().clip(RuleMin, RuleMax)
      op            = Op(hOp        .takeValue().clip(Op.MinId, Op.MaxId))
      wrap          = hWrap         .takeValue() != 0
      rollOff       = hRollOff      .takeValue().clip(0.0, 1.0)
      kaiserBeta    = hKaiserBeta   .takeValue().max(0.0)
      zeroCrossings = hZeroCrossings.takeValue().max(0)

      val newFrameSize = width * height
      if (frameSize != newFrameSize) {
        frameSize = newFrameSize
        frameBuf  = new Array(newFrameSize)
      }

      stage = 1
      if (dstDataReady) {
        dstDataReady = false
        processDstData()
      } else {
        requestNextDstData()
      }
    }

    private def processDstData(): Unit = {
      log("processDstData")
      assert (stage == 1)

      val dstRem    = hDst.bufRemain
      val frameRem  = frameSize - dstWritten
      val chunk     = if (dstRem == 0) {
        Util.fill(frameBuf, dstWritten, frameRem, hDst.takeValue())
        frameRem
      } else {
        val b = hDst.buf
        val _chunk = min(dstRem, frameRem)
        Util.copy(b.buf, hDst.offset, frameBuf, dstWritten, _chunk)
        hDst.offset += _chunk
        _chunk
      }
      dstWritten += chunk

      if (dstWritten < frameSize) {
        requestNextDstData()

      } else {
        stage = 2
        if (penDataReady) {
          penDataReady = false
          processPenData()
        } else {
          requestNextPenData()
        }
      }
    }

    private[this] var nextP = true  // ignore trigger at time zero

    private[this] var bufOut: BufD = _
    private[this] var outOff      = 0   // w.r.t. `bufOut`
    private[this] var outWritten  = 0   // pushed out plus `outOff`

    private def processPenData(): Unit = {
      log("processPenData")
      assert (stage == 2)

      // hSrc, hAlpha, hX, hY, hNext

      val srcRem    = hSrc  .bufRemain
      val alphaRem  = hAlpha.bufRemain
      val xRem      = hX    .bufRemain
      val yRem      = hY    .bufRemain
      val nextRem   = hNext .bufRemain

      var next      = false // hNext .takeValue() != 0
      var src       = 0.0   // hSrc  .takeValue()
      var alpha     = 0.0   // hAlpha.takeValue()
      var x         = 0.0   // hX    .takeValue()
      var y         = 0.0   // hY    .takeValue()

      var chunk     = Int.MaxValue
      if (srcRem    > 0 && srcRem   < chunk) chunk = srcRem
      if (alphaRem  > 0 && alphaRem < chunk) chunk = alphaRem
      if (xRem      > 0 && xRem     < chunk) chunk = xRem
      if (yRem      > 0 && yRem     < chunk) chunk = yRem
      if (nextRem   > 0 && nextRem  < chunk) chunk = nextRem

      def goToOut(): Unit = {
        nextP       = true  // so we don't re-trigger infinitely, aka `wasNextWindow`
        stage       = 3
        outWritten  = 0
        if (isAvailable(shape.out)) {
          processOutData()
        }
      }

      if (!hSrc.hasNext) {
        goToOut()
        return
      }

      assert (chunk < Int.MaxValue)
//      if (chunk == Int.MaxValue) chunk = 0

      while (chunk > 0) {
        next = hNext.peekValue() != 0
        if (!nextP && next /*|| !hSrc.hasNext*/) {
          goToOut()
          return
        }
        hNext.skipValue()

        src   = hSrc  .takeValue()
        alpha = hAlpha.takeValue()
        x     = hX    .takeValue()
        y     = hY    .takeValue()

        /*val v = */ process(x, y, src, alpha)

        chunk  -= 1
        nextP   = next
      }

      requestNextPenData()
    }

    // shape.out must be available
    private def processOutData(): Unit = {
      log("processOutData")
      assert (stage == 3)

      if (bufOut == null) {
        bufOut = ctrl.borrowBufD()
      }

      val chunk = min(bufOut.size - outOff, frameSize - outWritten)
      Util.copy(frameBuf, outWritten, bufOut.buf, outOff, chunk)
      outOff      += chunk
      outWritten  += chunk

      if (outOff == bufOut.size) {
        writeOut()
      }

      if (outWritten == frameSize) {
        if (hSrc.hasNext) {
          stage = 0
          if (auxDataReady) {
            auxDataReady = false
            processAuxData()
          } else {
            requestNextAuxData()
          }

        } else {
          writeOut()
          completeStage()
        }
      }
    }

    private def writeOut(): Unit = {
      log("writeOut")
      if (outOff > 0) {
        bufOut.size = outOff
        push(shape.out, bufOut)
        outOff = 0
        bufOut = null
      } else {
        freeOutBuffer()
      }
    }

    private def calcValue(Cs: Double, As: Double, Cd: Double, w: Double): Double =
      (rule: @switch) match {
        case Clear  | SrcOut  => 0.0
        case Src    | SrcIn   => Cs * w
        case Dst    | DstOver => Cd
        case SrcOver          => op(Cs * w, Cd*(1-(As * w)))
        case DstIn  | DstAtop => Cd*(As * w)
        case DstOut | Xor     => Cd*(1-(As * w))
        case SrcAtop          => op(Cd*(1-(As * w)), Cs * w)
      }

    private def process(x: Double, y: Double, Cs: Double, As: Double): Unit = {
      val _winBuf   = frameBuf
      val _width    = width
      val _height   = height
      val _wrap     = wrap

      val xq        = abs(x) % 1.0
      val xTi       = x.toInt
      val yq        = abs(y) % 1.0
      val yTi       = y.toInt

      // ------------------------ bicubic ------------------------
      if (zeroCrossings == 0) {

        val w1 = _width  - 1
        val h1 = _height - 1
        val x1 = if (_wrap) IntFunctions.wrap(xTi, 0, w1) else IntFunctions.clip(xTi, 0, w1)
        val y1 = if (_wrap) IntFunctions.wrap(yTi, 0, h1) else IntFunctions.clip(yTi, 0, h1)

        if (xq < 1.0e-20 && yq < 1.0e-20) {
          // short cut
          val winBufOff = y1 * _width + x1
          val Cd = _winBuf(winBufOff)
          val Cr = calcValue(Cs, As, Cd, 1.0)
          _winBuf(winBufOff) = Cr

        } else {
          // cf. https://en.wikipedia.org/wiki/Bicubic_interpolation
          // note -- we begin indices at `0` instead of `-1` here
          val x0  = if (x1 >  0) x1 - 1 else if (_wrap) w1 else 0
          val y0  = if (y1 >  0) y1 - 1 else if (_wrap) h1 else 0
          val x2  = if (x1 < w1) x1 + 1 else if (_wrap)  0 else w1
          val y2  = if (y1 < h1) y1 + 1 else if (_wrap)  0 else h1
          val x3  = if (x2 < w1) x2 + 1 else if (_wrap)  0 else w1
          val y3  = if (y2 < h1) y2 + 1 else if (_wrap)  0 else h1

          // XXX TODO --- we could save these multiplications here
          val y0s = y0 * _width
          val y1s = y1 * _width
          val y2s = y2 * _width
          val y3s = y3 * _width
          val f00 = _winBuf(y0s + x0)
          val f10 = _winBuf(y0s + x1)
          val f20 = _winBuf(y0s + x2)
          val f30 = _winBuf(y0s + x3)
          val f01 = _winBuf(y1s + x0)
          val f11 = _winBuf(y1s + x1)
          val f21 = _winBuf(y1s + x2)
          val f31 = _winBuf(y1s + x3)
          val f02 = _winBuf(y2s + x0)
          val f12 = _winBuf(y2s + x1)
          val f22 = _winBuf(y2s + x2)
          val f32 = _winBuf(y2s + x3)
          val f03 = _winBuf(y3s + x0)
          val f13 = _winBuf(y3s + x1)
          val f23 = _winBuf(y3s + x2)
          val f33 = _winBuf(y3s + x3)

          def bicubic(t: Double, f0: Double, f1: Double, f2: Double, f3: Double): Double = {
            // XXX TODO --- could save the next two multiplications
            val tt  = t * t
            val ttt = tt * t
            val c0  = 2 * f1
            val c1  = (-f0 + f2) * t
            val c2  = (2 * f0 - 5 * f1 + 4 * f2 - f3) * tt
            val c3  = (-f0  + 3 * f1 - 3 * f2 + f3) * ttt
            0.5 * (c0 + c1 + c2 + c3)
          }

          val b0 = bicubic(xq, f00, f10, f20, f30)
          val b1 = bicubic(xq, f01, f11, f21, f31)
          val b2 = bicubic(xq, f02, f12, f22, f32)
          val b3 = bicubic(xq, f03, f13, f23, f33)
          bicubic(yq, b0, b1, b2, b3)

          ???
        }
      }
      // ------------------------- sinc -------------------------
      else {

        ???
//        val _fltBuf   = fltBuf
//        val _fltBufD  = fltBufD
//        val _fltLenH  = fltLenH
//
//        var value     = 0.0
//
//        def xIter(dir: Boolean): Unit = {
//          var xSrcOffI  = if (dir) xTi else xTi + 1
//          val xq1       = if (dir) xq  else 1.0 - xq
//          var xFltOff   = xq1 * xFltIncr
//          var xFltOffI  = xFltOff.toInt
//          var xSrcRem   = if (wrap) Int.MaxValue else if (dir) xSrcOffI else width - xSrcOffI
//          xSrcOffI      = IntFunctions.wrap(xSrcOffI, 0, width - 1)
//
//          while ((xFltOffI < _fltLenH) && (xSrcRem > 0)) {
//            val xr  = xFltOff % 1.0  // 0...1 for interpol.
//            val xw  = _fltBuf(xFltOffI) + _fltBufD(xFltOffI) * xr
//
//            def yIter(dir: Boolean): Unit = {
//              var ySrcOffI  = if (dir) yTi else yTi + 1
//              val yq1       = if (dir) yq  else 1.0 - yq
//              var yFltOff   = yq1 * yFltIncr
//              var yFltOffI  = yFltOff.toInt
//              var ySrcRem   = if (wrap) Int.MaxValue else if (dir) ySrcOffI else height - ySrcOffI
//              ySrcOffI      = IntFunctions.wrap(ySrcOffI, 0, height - 1)
//
//              while ((yFltOffI < _fltLenH) && (ySrcRem > 0)) {
//                val yr        = yFltOff % 1.0  // 0...1 for interpol.
//                val yw        = _fltBuf(yFltOffI) + _fltBufD(yFltOffI) * yr
//                val winBufOff = ySrcOffI * width + xSrcOffI
//
//                // if (winBufOff > _winBuf.length) {
//                //   println(s"x $x, y $y, xT $xT, yT $yT, xSrcOffI $xSrcOffI, ySrcOffI $ySrcOffI, _widthIn ${_widthIn}, _heightIn ${_heightIn}")
//                // }
//
//                value += _winBuf(winBufOff) * xw * yw
//                if (dir) {
//                  ySrcOffI -= 1
//                  if (ySrcOffI < 0) ySrcOffI += height
//                } else {
//                  ySrcOffI += 1
//                  if (ySrcOffI == height) ySrcOffI = 0
//                }
//                ySrcRem  -= 1
//                yFltOff  += yFltIncr
//                yFltOffI  = yFltOff.toInt
//              }
//            }
//
//            yIter(dir = true )  // left -hand side of window
//            yIter(dir = false)  // right-hand side of window
//
//            if (dir) {
//              xSrcOffI -= 1
//              if (xSrcOffI < 0) xSrcOffI += width
//            } else {
//              xSrcOffI += 1
//              if (xSrcOffI == width) xSrcOffI = 0
//            }
//            xSrcRem  -= 1
//            xFltOff  += xFltIncr
//            xFltOffI  = xFltOff.toInt
//          }
//        }
//
//        xIter(dir = true )  // left -hand side of window
//        xIter(dir = false)  // right-hand side of window
//
//        value * xGain * yGain
      }
    }
  }
}