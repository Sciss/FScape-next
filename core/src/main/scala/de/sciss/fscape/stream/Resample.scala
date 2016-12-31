/*
 *  Resample.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.{Out1DoubleImpl, Out1LogicImpl, ResampleImpl, StageImpl, NodeImpl}

object Resample {
  def apply(in: OutD, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in           , stage.in0)
    b.connect(factor       , stage.in1)
    b.connect(minFactor    , stage.in2)
    b.connect(rollOff      , stage.in3)
    b.connect(kaiserBeta   , stage.in4)
    b.connect(zeroCrossings, stage.in5)
    stage.out
  }

  private final val name = "Resample"

  private type Shape = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"           ),
      in1 = InD (s"$name.factor"       ),
      in2 = InD (s"$name.minFactor"    ),
      in3 = InD (s"$name.rollOff"      ),
      in4 = InD (s"$name.kaiserBeta"   ),
      in5 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
    with ResampleImpl[Shape]
    with Out1LogicImpl[BufD, Shape]
    with Out1DoubleImpl[Shape] {

    // rather arbitrary, but > 1 increases speed; for matrix resample, we'd want very small to save memory
    protected val PAD = 32

    private[this] var bufIn           : BufD = _

    private[this] var _inMainValid  = false
    private[this] var _canReadMain  = false

    private[this] var winBuf      : Array[Double] = _   // circular

    // ---- handlers / constructor ----

    setHandler(in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in0)")
        _canReadMain = true
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in0)")
        if (_inMainValid) process() // may lead to `flushOut`
        else {
          if (!isAvailable(in0)) {
            println(s"Invalid process $in0")
            completeStage()
          }
        }
      }
    })

    // ---- start/stop ----

    override def preStart(): Unit = {
      super.preStart()
      pull(in0)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf  = null
    }

    protected def freeMainInputBuffers(): Unit =
      if (bufIn != null) {
        bufIn.release()
        bufIn = null
      }

    def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    protected def readMainIns(): Int = {
      freeMainInputBuffers()
      bufIn = grab(in0)
      tryPull(in0)
      _inMainValid = true
      _canReadMain = false
      bufIn.size
    }

    // ---- infra ----

    protected def in0             : InD  = shape.in0
    protected def inFactor        : InD  = shape.in1
    protected def inMinFactor     : InD  = shape.in2
    protected def inRollOff       : InD  = shape.in3
    protected def inKaiserBeta    : InD  = shape.in4
    protected def inZeroCrossings : InI  = shape.in5
    protected def out0            : OutD = shape.out

    protected def inMainValid: Boolean = _inMainValid
    protected def canReadMain: Boolean = _canReadMain

    protected def availableInFrames : Int = inMainRemain
    protected def availableOutFrames: Int = outRemain

    // ---- process ----

    protected def processChunk(): Boolean = resample()

    protected def allocWinBuf(len: Int): Unit =
      winBuf = new Array[Double](len)

    protected def clearWinBuf(off: Int, len: Int): Unit =
      Util.clear(winBuf, off, len)

    protected def copyInToWinBuf(winOff: Int, len: Int): Unit = {
      Util.copy(bufIn.buf, inMainOff, winBuf, winOff, len)
      inMainOff    += len
      inMainRemain -= len
    }

    private[this] var value = 0.0

    protected def clearValue(): Unit =
      value = 0.0

    protected def addToValue(winOff: Int, weight: Double): Unit =
      value += winBuf(winOff) * weight

    protected def copyValueToOut(): Unit = {
      bufOut0.buf(outOff) = value * gain
      outOff    += 1
      outRemain -= 1
    }
  }
}