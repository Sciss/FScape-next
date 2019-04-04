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

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape14, Inlet}
import de.sciss.fscape.graph.PenImage._
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.{DoubleFunctions => rd}
import de.sciss.numbers.{IntFunctions => ri}
import graph.BinaryOp.Op

import scala.math.max

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
    extends NodeImpl(name, layer, shape) { logic =>

    // structure of inputs:
    //
    // "aux per image": in3 width, in4 height, in8 rule, in9 op, in10 wrap, in11 rollOff, in12 kaiser, in13 zc
    // "during image": in0 src, in1 alpha, in2 dst, in5 x, in6 y, in7 next
    //
    // "hot": in0 src
    // "may end any time": in1 alpha, in2 dst, in5 5, in6 y, in7 next

//    private[this] var auxDataOpen   = 8
    private[this] var auxDataRem    = 8
    private[this] var auxDataReady  = false

    private[this] var width : Int = _
    private[this] var height: Int = _
    private[this] var rule  : Int = _
    private[this] var op    : Op  = _

    private[this] var wrap          : Boolean = _
    private[this] var rollOff       : Double  = _
    private[this] var kaiserBeta    : Double  = _
    private[this] var zeroCrossings : Int     = _

    private[this] val auxInHandlers = Array[AuxInHandler[_, _]](
      new AuxInHandler((v: Int)     => width  = max(1, v))                (shape.in3),
      new AuxInHandler((v: Int)     => height = max(1, v))                (shape.in4),
      new AuxInHandler((v: Int)     => rule   = ri.clip(v, RuleMin, RuleMax))(shape.in8),
      new AuxInHandler((v: Int)     => op     = Op(ri.clip(v, Op.MinId, Op.MaxId)))(shape.in9),
      new AuxInHandler((v: Int)     => wrap   = v != 0)                   (shape.in10),
      new AuxInHandler((v: Double)  => rollOff    = rd.clip(v, 0.0, 1.0)) (shape.in11),
      new AuxInHandler((v: Double)  => kaiserBeta = max(0.0, v))          (shape.in12),
      new AuxInHandler((v: Int)     => zeroCrossings  = v)                (shape.in13)
    )

    private[this] val hSrc    = new MainInDHandler(shape.in0)
    private[this] val hAlpha  = new MainInDHandler(shape.in1)
    private[this] val hDst    = new MainInDHandler(shape.in2)
    private[this] val hX      = new MainInDHandler(shape.in5)
    private[this] val hY      = new MainInDHandler(shape.in6)
    private[this] val hNext   = new MainInIHandler(shape.in7)

    private[this] val mainInHandlers = Array[MainInHandler](
      hSrc, hAlpha, hDst, hX, hY, hNext
    )

    override protected def stopped(): Unit = {
      super.stopped()
      auxInHandlers.foreach(_.freeBuffer())
    }

    private final class AuxInHandler[A, E <: BufElem[A]](set: A => Unit)(val in: Inlet[E])
      extends InHandler {

      private[this] var hasValue      = false
      private[this] var everHadValue  = false
      private[this] var buf: E = _
      private[this] var offset = 0

      override def toString: String = s"$logic.$in"

      def freeBuffer(): Unit =
        if (buf != null) {
          buf.release()
          buf = null.asInstanceOf[E]
        }

      def next(): Unit = {
        hasValue = false
        if (buf != null) {
          takeValue()
        } else {
          if (isAvailable(in)) onPush()
        }
      }

      private def takeValue(): Unit = {
        val i = buf.buf(offset)
        offset += 1
        set(i)
        if (offset == buf.size) {
          freeBuffer()
        }
        ackValue()
      }

      def onPush(): Unit = if (!hasValue) {
        assert (buf == null)
        buf     = grab(in)
        assert (buf.size > 0)
        offset  = 0
        takeValue()
        tryPull(in)
      }

      private def ackValue(): Unit = {
        hasValue      = true
        everHadValue  = true
        auxDataRem -= 1
        if (auxDataRem == 0) {
          auxValuesReady()
        }
      }

      def checkPushed(): Unit =
        if (isAvailable(in)) onPush()

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

    private abstract class MainInHandler extends InHandler {

    }

    private final class MainInDHandler(val in: InD) extends MainInHandler {
      def onPush(): Unit = ???

      override def onUpstreamFinish(): Unit = super.onUpstreamFinish()

      setHandler(in, this)
    }

    private final class MainInIHandler(val in: InI) extends MainInHandler {
      def onPush(): Unit = ???

      override def onUpstreamFinish(): Unit = super.onUpstreamFinish()

      setHandler(in, this)
    }

    private def turnToNextImage(): Unit = {
      auxDataReady = false
      assert (auxDataRem == 0)
      auxDataRem = auxInHandlers.count(h => !isClosed(h.in))
      if (auxDataRem > 0) {
        auxInHandlers.foreach(h => if (!isClosed(h.in)) h.next())
      }
    }

    private def auxValuesReady(): Unit = {
      assert (!auxDataReady)
      auxDataReady = true
      ???
    }
  }
}