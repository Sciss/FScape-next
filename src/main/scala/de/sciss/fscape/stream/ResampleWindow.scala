/*
 *  ResampleWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import java.io.RandomAccessFile
import java.nio.DoubleBuffer
import java.nio.channels.FileChannel

import akka.stream.stage.{GraphStageLogic, InHandler}
import akka.stream.{Attributes, FanInShape7}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{Out1DoubleImpl, Out1LogicImpl, ResampleImpl, StageImpl, StageLogicImpl}

object ResampleWindow {
  def apply(in: OutD, size: OutI, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in           , stage.in0)
    b.connect(size         , stage.in1)
    b.connect(factor       , stage.in2)
    b.connect(minFactor    , stage.in3)
    b.connect(rollOff      , stage.in4)
    b.connect(kaiserBeta   , stage.in5)
    b.connect(zeroCrossings, stage.in6)
    stage.out
  }

  private final val name = "ResampleWindow"

  private type Shape = FanInShape7[BufD, BufI, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape7(
      in0 = InD (s"$name.in"           ),
      in1 = InI (s"$name.size"         ),
      in2 = InD (s"$name.factor"       ),
      in3 = InD (s"$name.minFactor"    ),
      in4 = InD (s"$name.rollOff"      ),
      in5 = InD (s"$name.kaiserBeta"   ),
      in6 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with ResampleImpl[Shape]
      with Out1LogicImpl[BufD, Shape]
      with Out1DoubleImpl[Shape] {

    protected val PAD = 1

    private[this] var bufIn : BufD = _

    private[this] var _inMainValid  = false
    private[this] var _canReadMain  = false

    // size of a window, not the resample buffer
    private[this] var size: Int = _
    private[this] var valueArr: Array[Double] = _

    private[this] var winBuf: DoubleBuffer      = _
    private[this] var winF  : File              = _
    private[this] var winRaf: RandomAccessFile  = _

    // ---- handlers / constructor ----

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush(${shape.in0})")
        _canReadMain = true
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish(${shape.in0})")
        if (_inMainValid) process() // may lead to `flushOut`
        else {
          if (!isAvailable(shape.in0)) {
            println(s"Invalid process ${shape.in0}")
            completeStage()
          }
        }
      }
    })

    // ---- start/stop ----

    override def preStart(): Unit = {
      super.preStart()
      pull(shape.in0)
    }

    override def postStop(): Unit = {
      super.postStop()
      freeWinBuffer()
    }

    private def freeWinBuffer(): Unit = {
      if (winRaf != null) {
        winRaf.close()
        winF.delete()
        winRaf = null
        winF   = null
      }
      winBuf = null
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
      bufIn = grab(shape.in0)
      tryPull(shape.in0)
      _inMainValid = true
      _canReadMain = false
      bufIn.size
    }

    // ---- infra ----

    protected def in0             : InD  = shape.in0

    protected def inFactor        : InD  = shape.in2
    protected def inMinFactor     : InD  = shape.in3
    protected def inRollOff       : InD  = shape.in4
    protected def inKaiserBeta    : InD  = shape.in5
    protected def inZeroCrossings : InI  = shape.in6
    protected def out0            : OutD = shape.out

    protected def inMainValid: Boolean = _inMainValid
    protected def canReadMain: Boolean = _canReadMain

    protected def availableInFrames : Int = ???
    protected def availableOutFrames: Int = ???

    // ---- process ----

    protected def allocWinBuf(len: Int): Unit = {
      freeWinBuffer()
      val bufSize = len * size
      if (bufSize <= ctrl.nodeBufferSize) {
        val arr   = new Array[Double](bufSize)
        winBuf    = DoubleBuffer.wrap(arr)
      } else {
        winF      = ctrl.createTempFile()
        winRaf    = new RandomAccessFile(winF, "rw")
        val fch   = winRaf.getChannel
        val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, bufSize * 8)
        winBuf    = bb.asDoubleBuffer()
      }
    }

    protected def clearWinBuf(off: Int, len: Int): Unit = {
      ??? // Util.clear(winBuf, off, len)
    }

    protected def copyInToWinBuf(winOff: Int, len: Int): Unit = {
      ??? // Util.copy(bufIn.buf, inOff, winBuf, winOff, len)
    }

    protected def clearValue(): Unit = Util.clear(valueArr, 0, valueArr.length)

    protected def addToValue(winOff: Int, weight: Double): Unit = {
      val sz   = size
      val off1 = winOff * sz
      val b    = winBuf
      b.position(off1)
      val arr  = valueArr
      var i = 0
      while (i < sz) {
        arr(i) += b.get() * weight
        i += 1
      }
    }

    protected def copyValueToOut(gain: Double): Unit = {
      ??? // bufOut0.buf(outOff) = value * gain
    }
  }
}