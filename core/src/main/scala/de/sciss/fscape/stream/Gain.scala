/*
 *  Gain.scala
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
import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

object Gain {
  def apply(in: ISeq[OutD], mul: OutD, normalized: OutI, bipolar: OutI)(implicit b: Builder): Vec[OutD] = {
    val numChannels = in.size
    val stage0      = new Stage(numChannels = numChannels, layer = b.layer)
    val stage       = b.add(stage0)
    b.connect(mul         , stage.inMul     )
    b.connect(normalized  , stage.inNorm    )
    b.connect(bipolar     , stage.inBipolar )

    (in zip stage.ins).foreach { case (i, j) => b.connect(i, j) }

    stage.outs
  }

  private final val name = "Gain"

  private final case class Shape(inMul: InD, inNorm: InI, inBipolar: InI, ins: Vec[InD], outs: Vec[OutD])
    extends akka.stream.Shape {

    val inlets  : Vec[Inlet [_]] = inMul +: inNorm +: inBipolar +: ins
    val outlets : Vec[Outlet[_]] = outs

    def deepCopy(): Shape = {
      Shape(inMul = inMul.carbonCopy(), inNorm = inNorm.carbonCopy(), inBipolar = inBipolar.carbonCopy(),
        ins = ins.map(_.carbonCopy()), outs = outs.map(_.carbonCopy()))
    }
  }

  private final class Stage(numChannels: Int, layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = Shape(
      inMul     = InD(s"$name.mul"        ),
      inNorm    = InI(s"$name.normalized" ),
      inBipolar = InI(s"$name.bipolar"    ),
      ins       = Vector.tabulate(numChannels)(ch => InD  (s"$name.in${ ch+1}")),
      outs      = Vector.tabulate(numChannels)(ch => OutD (s"$name.out${ch+1}")),
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) { self =>

    private[this] val numChannels = shape.ins.size

    private[this] var mul       : Double  = _
    private[this] var add       : Double  = _
    private[this] var normalized: Boolean = _
    private[this] var bipolar   : Boolean = _

    private[this] var auxRemain = 3 // mul, normalized, bipolar
    private[this] var auxReady  = false

    // for normalization
    private[this] var fileBuffers: Array[FileBuffer] = _
    private[this] var minValue          = Double.PositiveInfinity
    private[this] var maxValue          = Double.NegativeInfinity
    private[this] var normFileBufReady  = false

    private def decreaseAuxRemain(): Unit = {
      auxRemain -= 1
      if (auxRemain == 0) {
        if (bipolar) {
          fileBuffers = Array.fill(numChannels)(FileBuffer())
          auxReady = true
        }
      }
    }

    override protected def stopped(): Unit = {
      super.stopped()
      if (fileBuffers != null) {
        fileBuffers.foreach { fb =>
          if (fb != null) fb.dispose()
        }
        fileBuffers = null
      }
    }

    private class AuxHandler[A, E <: BufElem[A]](set: A => Unit)(in: Inlet[E]) extends InHandler {
      private[this] var done  = false

      override def toString: String = in.toString

      def onPush(): Unit = {
        logStream(s"onPush() $this")
        val b = grab(in)
        if (!done && b.size > 0) {
          val v = b.buf(0)
          done  = true
          set(v)
          decreaseAuxRemain()
        }
        b.release()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish() $this")
        if (!done) super.onUpstreamFinish()
      }
    }

    private class MainInHandler(ch: Int) extends InHandler {
      override def toString: String = shape.ins(ch).toString

      def onPush(): Unit = {
        logStream(s"onPush() $this")
        inPushed(ch)
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish() $this")
        inClosed(ch)
      }
    }

    private class MainOutHandler(ch: Int) extends OutHandler {
      override def toString: String = shape.outs(ch).toString

      def onPull(): Unit = {
        logStream(s"onPull() $this")
        outPulled(ch)
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish() $this")
        ??? // outClosed(ch)
      }
    }

    setHandler(shape.inMul    , new AuxHandler((v: Double)  => mul        = v     )(shape.inMul    ))
    setHandler(shape.inNorm   , new AuxHandler((v: Int)     => normalized = v != 0)(shape.inNorm   ))
    setHandler(shape.inBipolar, new AuxHandler((v: Int)     => bipolar    = v != 0)(shape.inBipolar))

    for (ch <- 0 until numChannels) {
      setHandler(shape.ins  (ch), new MainInHandler (ch))
      setHandler(shape.outs (ch), new MainOutHandler(ch))
    }

    //////////////

    private def analyzeNorm(b: BufD): Unit = {
      val buf   = b.buf
      var i     = 0
      val sz    = b.size
      var _min  = minValue
      var _max  = maxValue
      while (i < sz) {
        val v = buf(i)
        if (v < _min) _min = v
        if (v > _max) _max = v
        i += 1
      }
      minValue = _min
      maxValue = _max
    }

    private def inPushed(ch: Int): Unit = if (auxReady) {
      val in = shape.ins(ch)
      if (normalized) {
        val b   = grab(in)
        val fb  = fileBuffers(ch)
        fb.write(b.buf, 0, b.size)
        analyzeNorm(b)
        b.release()
        if (isClosed(in)) {
          checkNormInputsDone()

        } else {
          pull(in)
        }

      } else {
        val out = shape.outs(ch)
        if (isAvailable(out)) {
          pipeDirect(in, out)
        }
      }
    }

    private def checkNormInputsDone(): Unit =
      if (shape.ins.forall(in => isClosed(in) && !isAvailable(in))) {
        normFileBufReady = true
        fileBuffers.foreach(_.rewind())
        add   = if (bipolar) 0.0 else -minValue
        mul   = if (bipolar) {
          val ceil = math.max(math.abs(minValue), math.abs(minValue))
          if (ceil > 0.0) mul/ceil else 0.0

        } else {
          val span = maxValue - minValue
          if (span > 0.0) mul/span else 0.0
        }
        checkPipeNorm()
      }

    private def checkPipeNorm(): Unit = {
      var numClosed = 0
      var numDone   = 0
      var ch = 0
      while (ch < numChannels) {
        val out     = shape.outs(ch)
        if (isClosed(out)) {
          numClosed += 1
        } else if (isAvailable(out)) {
          val fb = fileBuffers(ch)
          if (fb == null) {
            numDone += 1
          } else {
            val done = pipeNorm(fileBuffers(ch), out)
            if (done) {
              fb.dispose()
              fileBuffers(ch) = null
              numDone += 1
            }
          }
        }
        ch += 1
      }

      if (numClosed == numChannels || numDone == numChannels) completeStage()
    }

    private def pipeNorm(fileBuf: FileBuffer, out: OutD): Boolean = {
      val rem = fileBuf.numFrames - fileBuf.position
      if (rem == 0L) return true

      val b     = ctrl.borrowBufD()
      val chunk = math.min(rem, b.size).toInt
      b.size    = chunk
      fileBuf.read(b.buf, 0, chunk)
      if (add != 0.0) Util.add(b.buf, 0, chunk, add)
      if (mul != 1.0) Util.mul(b.buf, 0, chunk, mul)
      push(out, b)
      false
    }

    private def outPulled(ch: Int): Unit = if (auxReady) {
      val out = shape.outs(ch)
      if (normalized) {
        if (normFileBufReady) {
          checkPipeNorm()
        }

      } else {
        val in = shape.ins(ch)
        if (isAvailable(in)) {
          pipeDirect(in, out)
        }
      }
    }

    private def pipeDirect(in: InD, out: OutD): Unit = {
      val b = grab(in)
      Util.mul(b.buf, 0, b.size, mul)
      push(out, b)
      tryPull(in)
    }

    private def inClosed(ch: Int): Unit = {
      val in = shape.ins(ch)
      if (isAvailable(in)) {
        inPushed(ch)

      } else {
        if (auxReady) {
          ???
          if (normalized) {

          } else {

          }

        } else {
          completeStage()
        }
      }
    }
  }
}