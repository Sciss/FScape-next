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

object PenImage {
  def apply(src: OutD, alpha: OutD, dst: OutD, width: OutI, height: OutI, x: OutD, y: OutD, next: OutI,
            rule: OutI, op: OutI,
            wrap: OutI,
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
    // - then main polling until a trigger in `next` is received
    // - main polling distinguishes between too groups of signals:
    //   1. `dst` needs to be read in first, as it creates the "background",
    //      i.e. we need as much as `width * height` values (or truncate if
    //      `dst` ends prematurely).
    //   2. `src`, `alpha`, `x`, `y`, `next` are synchronised.

//    private[this] var auxDataOpen   = 8
    private[this] var auxDataRem    = 8
    private[this] var auxDataReady  = false

    private[this] var mainDataRem   = 5
    private[this] var mainDataReady = false

    private[this] var width         : Int     = _
    private[this] var height        : Int     = _
    private[this] var rule          : Int     = _
    private[this] var op            : Op      = _
    private[this] var wrap          : Boolean = _
    private[this] var rollOff       : Double  = _
    private[this] var kaiserBeta    : Double  = _
    private[this] var zeroCrossings : Int     = _

    private[this] val hSrc            = new MainInHandler [Double , BufD](shape.in0 )
    private[this] val hAlpha          = new MainInHandler [Double , BufD](shape.in1 )
    private[this] val hDst            = new MainInHandler [Double , BufD](shape.in2 )
    private[this] val hX              = new MainInHandler [Double , BufD](shape.in5 )
    private[this] val hY              = new MainInHandler [Double , BufD](shape.in6 )
    private[this] val hNext           = new MainInHandler [Int    , BufI](shape.in7 )

    private[this] val hWidth          = new AuxInHandler  [Int    , BufI](shape.in3 )
    private[this] val hHeight         = new AuxInHandler  [Int    , BufI](shape.in4 )
    private[this] val hRule           = new AuxInHandler  [Int    , BufI](shape.in8 )
    private[this] val hOp             = new AuxInHandler  [Int    , BufI](shape.in9 )
    private[this] val hWrap           = new AuxInHandler  [Int    , BufI](shape.in10)
    private[this] val hRollOff        = new AuxInHandler  [Double , BufD](shape.in11)
    private[this] val hKaiserBeta     = new AuxInHandler  [Double , BufD](shape.in12)
    private[this] val hZeroCrossings  = new AuxInHandler  [Int    , BufI](shape.in13)

    private[this] val mainInHandlers = Array[MainInHandler[_, _]](
      hSrc, hAlpha, /*hDst, */ hX, hY, hNext
    )

    private[this] val auxInHandlers = Array[InHandlerImpl[_, _]](
      hWidth, hHeight, hRule, hOp, hWrap, hRollOff, hKaiserBeta, hZeroCrossings
    )

    override protected def stopped(): Unit = {
      super.stopped()
      auxInHandlers .foreach(_.freeBuffer())
      mainInHandlers.foreach(_.freeBuffer())
      frameBuf = null
    }

    private abstract class InHandlerImpl[A, E <: BufElem[A]](in: Inlet[E])
      extends InHandler {

      private[this] var hasValue      = false
      private[this] var everHadValue  = false

      final var buf       : E   = _
      final var offset    : Int = 0
      final var mostRecent: A   = _

      // ---- abstract ----

      protected def notifyValue(): Unit

      // ---- impl ---

      final def bufRemain: Int = if (buf == null) 0 else buf.size - offset

      override final def toString: String = s"$logic.$in"

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

      final def onPush(): Unit = if (!hasValue) {
        assert (buf == null)
        buf     = grab(in)
        assert (buf.size > 0)
        offset  = 0
        ackValue()
        tryPull(in)
      }

      private def ackValue(): Unit = {
        hasValue      = true
        everHadValue  = true
        notifyValue()
      }

      final override def onUpstreamFinish(): Unit = {
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

    private final class MainInHandler[A, E <: BufElem[A]](val in: Inlet[E])
      extends InHandler {

      private[this] var hasValue      = false
      private[this] var everHadValue  = false

      var buf       : E   = _
      var offset    : Int = 0
      var mostRecent: A   = _

      def bufRemain: Int = if (buf == null) 0 else buf.size - offset

      override def toString: String = s"$logic.$in"

      def hasNext: Boolean =
        (buf != null) || !isClosed(in) || isAvailable(in)

      def freeBuffer(): Unit =
        if (buf != null) {
          mostRecent = buf.buf(buf.size - 1)
          buf.release()
          buf = null.asInstanceOf[E]
        }

      def next(): Unit = {
        hasValue = false
        if (bufRemain > 0) {
          ackValue()
        } else {
          freeBuffer()
          if (isAvailable(in)) onPush()
        }
      }

      def onPush(): Unit = if (!hasValue) {
        assert (buf == null)
        buf     = grab(in)
        assert (buf.size > 0)
        offset  = 0
        ackValue()
        tryPull(in)
      }

      private def ackValue(): Unit = {
        hasValue      = true
        everHadValue  = true
        mainDataRem -= 1
        if (mainDataRem == 0) {
          notifyMainDataReady()
        }
      }

      override def onUpstreamFinish(): Unit = {
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

    // ---- out handler ----

    def onPull(): Unit = ???

    override def onDownstreamFinish(): Unit = {
      super.onDownstreamFinish()
    }

    // ---- stages ----

    private[this] var frameSize : Int = -1

    private[this] var frameBuf  : Array[Double] = _

    private[this] var stage = 0   // 0 gather aux, 1 fill dst, 2 pen

    private def turnToNextAuxData(): Unit = {
      stage         = 0
      auxDataReady  = false
      assert (auxDataRem == 0)
      auxDataRem = auxInHandlers.count(_.hasNext)
      if (auxDataRem > 0) {
        auxInHandlers.foreach(h => if (h.hasNext) h.next())
      } else {
        notifyAuxDataReady()
      }
    }

    private def notifyAuxDataReady(): Unit = {
      assert (!auxDataReady)
      auxDataReady = true

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

      if (stage == 0) {
        turnToNextDstData()
      }
    }

    private def turnToNextDstData(): Unit = {
      stage = 1
      mainDataReady = false
      assert (mainDataRem == 0)
      mainDataRem = mainInHandlers.count(_.hasNext)
      if (mainDataRem > 0) {
        mainInHandlers.foreach(h => if (h.hasNext) h.next())
      }
    }

    private def turnToNextMainData(): Unit = {
      stage = 2
      mainDataReady = false
      assert (mainDataRem == 0)
      mainDataRem = mainInHandlers.count(_.hasNext)
      if (mainDataRem > 0) {
        mainInHandlers.foreach(h => if (h.hasNext) h.next())
      }
    }

    private def notifyMainDataReady(): Unit = {
      assert (!mainDataReady)
      mainDataReady = true
      if (auxDataReady) {
        ???
      }
    }

    // ---- process ----
  }
}