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
import akka.stream.{Attributes, FanInShape7, Inlet}
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

    private[this] var bufIn   : BufD = _
    private[this] var bufSize : BufI = _

    private[this] var _inMainValid  = false
    private[this] var _canReadMain  = false

    // size of a window, not the resample buffer
    private[this] var size: Int = 0

    // this serves as the collecting and emitting window
    private[this] var valueArr: Array[Double] = _

    private[this] var winBuf: DoubleBuffer      = _
    private[this] var winF  : File              = _
    private[this] var winRaf: RandomAccessFile  = _

    private[this] var _availableInFrames  = 0
    private[this] var _availableOutFrames = 0

    private[this] var stateInToVal  = true
    private[this] var stateValToOut = false
    private[this] var valOff        = 0


    // ---- infra ----

    protected def in0             : InD  = shape.in0
    private[this] val in1         : InI  = shape.in1

    protected def inFactor        : InD  = shape.in2
    protected def inMinFactor     : InD  = shape.in3
    protected def inRollOff       : InD  = shape.in4
    protected def inKaiserBeta    : InD  = shape.in5
    protected def inZeroCrossings : InI  = shape.in6
    protected def out0            : OutD = shape.out

    protected def inMainValid: Boolean = _inMainValid
    protected def canReadMain: Boolean = _canReadMain

    // ---- handlers / constructor ----

    private def updateCanReadMain(): Unit =
      _canReadMain = isAvailable(in0) &&
        ((isClosed(in1) && _inMainValid) || isAvailable(in1))

    private final class MainInHandler(in: Inlet[_], isProcess: Boolean) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        updateCanReadMain()
        if (_canReadMain) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        updateCanReadMain()
        if (_inMainValid) {
          if (isProcess || _canReadMain) process() // may lead to `flushOut`
        } else {
          if (!isAvailable(in)) {
            println(s"Invalid process $in")
            completeStage()
          }
        }
      }

      setHandler(in, this)
    }

    new MainInHandler(in0, isProcess = true )
    new MainInHandler(in1, isProcess = false)

    // ---- start/stop ----

    override def preStart(): Unit = {
      super.preStart()
      pull(in0)
      pull(in1)
    }

    override def postStop(): Unit = {
      super.postStop()
      valueArr = null
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

    protected def freeMainInputBuffers(): Unit = {
      if (bufIn != null) {
        bufIn.release()
        bufIn = null
      }
      if (bufSize != null) {
        bufSize.release()
        bufSize = null
      }
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

      if (isAvailable(in1)) {
        bufSize = grab(in1)
        tryPull(in1)
        if (size == 0) {
          size = math.max(1, bufSize.buf(0))
          valueArr = new Array[Double](size)
        }
      }

      _inMainValid = true
      _canReadMain = false
      bufIn.size
    }

    // ---- process ----

//    @inline
//    private[this] def stateResample = !(stateInToVal || stateValToOut)

    def STATE(): String = if (stateInToVal) "in -> value" else if (stateValToOut) "value -> out" else "resample"

    protected def processChunk(): Boolean = {
      val before = STATE()
      val res = processChunkX()
      val after = STATE()
      println(s"BEFORE: $before; AFTER: $after; res = $res")
      res
    }

    protected def processChunkX(): Boolean =
      if (stateInToVal) {
        val isFlush = shouldComplete()
        (inMainRemain > 0 || isFlush) && {
          val valOff0 = valOff
          val sz      = size
          val valRem  = sz - valOff0
          val chunk   = math.min(inMainRemain, valRem)
          Util.copy(bufIn.buf, inMainOff, valueArr, valOff0, chunk)
          inMainOff     += chunk
          inMainRemain  -= chunk
          val valOff1    = valOff0 + chunk
          if (isFlush) Util.clear(valueArr, valOff1, valRem - chunk)
          if (isFlush || valOff1 == sz) {
            // ready for resample
            stateInToVal = false
            _availableInFrames = 1
            valOff = 0
          } else {
            valOff = valOff1
          }
          true
        }

      } else if (stateValToOut) {
        (outRemain > 0) && {
          val valOff0 = valOff
          val sz      = size
          val valRem  = sz - valOff0
          val chunk   = math.min(outRemain, valRem)
          Util.copy(valueArr, valOff0, bufOut0.buf, outOff, chunk)
          outOff     += chunk
          outRemain  -= chunk
          val valOff1    = valOff0 + chunk
          if (valOff1 == sz) {
            // ready for next read
            stateValToOut = false
            stateInToVal  = true
            valOff = 0
          } else {
            valOff = valOff1
          }
          true
        }

      } else {
        resample()
      }

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

    protected def availableInFrames : Int = _availableInFrames
    protected def availableOutFrames: Int = _availableOutFrames

    protected def clearWinBuf(off: Int, len: Int): Unit = {
      val b     = winBuf
      val sz    = size
      val off1  = off * sz
      b.position(off1)
      var i = 0
      while (i < sz) {
        b.put(0.0)
        i += 1
      }
    }

    protected def copyInToWinBuf(winOff: Int, len: Int): Unit = {
      assert(len == 1 && _availableInFrames == 1)
      val b     = winBuf
      val off1  = winOff * size

      b.position(off1)
      b.put(valueArr)

      _availableInFrames  = 0
      _availableOutFrames = 1
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

    protected def copyValueToOut(): Unit = {
      assert(_availableOutFrames == 1)
      Util.mul(valueArr, 0, size, gain)
      _availableOutFrames = 0
    }
  }
}