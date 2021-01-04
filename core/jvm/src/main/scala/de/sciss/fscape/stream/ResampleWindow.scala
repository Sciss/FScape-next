/*
 *  ResampleWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
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

import akka.stream.{Attributes, FanInShape7}
import de.sciss.file._
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.{NodeImpl, ResampleImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.math.{max, min}

object ResampleWindow {
  def apply(in: OutD, size: OutI, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
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

  private type Shp = FanInShape7[BufD, BufI, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape7(
      in0 = InD (s"$name.in"           ),
      in1 = InI (s"$name.size"         ),
      in2 = InD (s"$name.factor"       ),
      in3 = InD (s"$name.minFactor"    ),
      in4 = InD (s"$name.rollOff"      ),
      in5 = InD (s"$name.kaiserBeta"   ),
      in6 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends ResampleImpl(name, layer, shape) {

    protected val PAD = 1

    // size of a window, not the resample buffer
    private[this] var size: Int = 0

    // this serves as the collecting window
    private[this] var inArr: Array[Double] = _
    // this serves as the emitting window
    private[this] var outArr: Array[Double] = _

    private[this] var winBuf: DoubleBuffer      = _
    private[this] var winF  : File              = _
    private[this] var winRaf: RandomAccessFile  = _

    // true when we have begun copying data from the
    // input buffer to the value array.
    private[this] var lockInToVal   = true

    // true when the value array has been filled with
    // input data. will be cleared in `copyInToWinBuf`
    // after we copied the value array into the resampling
    // algorithm's window buffer.
    private[this] var lockValToWin = false

    // true when we have begun copying data from the
    // value array to the output buffer.
    private[this] var lockValToOut  = false
    private[this] var valOff        = 0

    // ---- infra ----

    protected val hIn            : InDMain  = InDMain (this, shape.in0)
    protected val hSize          : InIAux   = InIAux  (this, shape.in1)(max(1, _))
    protected val hFactor        : InDAux   = InDAux  (this, shape.in2)(max(0.0, _))
    protected val hMinFactor     : InDAux   = InDAux  (this, shape.in3)(max(0.0, _))
    protected val hRollOff       : InDAux   = InDAux  (this, shape.in4)(_.clip(0.0, 1.0))
    protected val hKaiserBeta    : InDAux   = InDAux  (this, shape.in5)(max(1.0, _))
    protected val hZeroCrossings : InIAux   = InIAux  (this, shape.in6)(max(1, _))
    protected val hOut           : OutDMain = OutDMain(this, shape.out)

    // ---- start/stop ----

    override protected def stopped(): Unit = {
      super.stopped()
      inArr  = null
      outArr = null
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

    private[this] var needsSize = true

    // ---- process ----

    protected def processChunk(): Boolean = {
      if (needsSize) {
        if (!hSize.hasNext) return false
        size      = hSize.next()
        inArr     = new Array[Double](size)
        outArr    = new Array[Double](size)
        needsSize = false
      }

      if (lockInToVal || !lockValToWin) {
        val isFlush       = hIn.isDone
        val inMainRemain  = hIn.available
        (inMainRemain > 0 || isFlush) && {
          // debugLog("---> read")
          val sz      = size
          val valOff0 = valOff
          val valRem  = sz - valOff0
          val chunk   = min(inMainRemain, valRem)
          if (chunk > 0) hIn.nextN(inArr, valOff0, chunk)
          val valOff1    = valOff0 + chunk
          if (isFlush) Util.clear(inArr, valOff1, valRem - chunk)
          if (isFlush || valOff1 == sz) {
            // debugLog("---> read DONE")
            // ready for resample
            lockInToVal         = false
            lockValToWin        = true
            valOff              = 0
            resample()

          } else {
            lockInToVal         = true
            valOff              = valOff1
          }
          true
        }

      } else if (lockValToOut) {
        val outRemain = hOut.available
        (outRemain > 0) && {
          // debugLog("---> write")
          val valOff0 = valOff
          val sz      = size
          val valRem  = sz - valOff0
          val chunk   = min(outRemain, valRem)
          hOut.nextN(outArr, valOff0, chunk)
          val valOff1    = valOff0 + chunk
          if (valOff1 == sz) {
            // debugLog("---> write DONE")
            // ready for next read
            lockValToOut  = false
            valOff        = 0
          } else {
            valOff        = valOff1
          }
          true
        }

      } else if (lockValToWin || !(lockInToVal || lockValToOut)) {
        resample()

      } else false
    }

    protected def allocWinBuf(len: Int): Unit = {
      freeWinBuffer()
      val bufSizeL = len * size

      def checkSz(n: Long): Unit =
        if (n < 0 || n > 0x7FFFFFFF) {
          throw new IllegalArgumentException(s"ResampleWindow: buffer size too large: $len * $size")
        }

      checkSz(bufSizeL)
      val bufSize = bufSizeL.toInt
      if (bufSize <= ctrl.nodeBufferSize) {
        val arr   = new Array[Double](bufSize)
        winBuf    = DoubleBuffer.wrap(arr)
      } else {
        winF      = ctrl.createTempFile()
        winRaf    = new RandomAccessFile(winF, "rw")
        val fch   = winRaf.getChannel
        val szBtL = bufSizeL * 8
        checkSz(szBtL)
        val szBt  = szBtL.toInt
        val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, szBt)
        winBuf    = bb.asDoubleBuffer()
      }
    }

    protected def availableInFrames : Int = {
      val res = if (lockValToWin) 1 else 0
      // debugLog(s"<--- availableInFrames  $res")
      res
    }

    protected def availableOutFrames: Int = {
      val res = if (/* lockInToVal || lockValToWin || */ lockValToOut) 0 else 1
      // debugLog(s"<--- availableOutFrames $res")
      res
    }

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
      // debugLog("---> copyInToWinBuf")
      assert(len == 1 && !lockInToVal && lockValToWin /* && !lockValToOut */)
      val b     = winBuf
      val off1  = winOff * size

      b.position(off1)
      b.put(inArr)

      lockValToWin = false
    }

    protected def clearValue(): Unit = {
      // debugLog("---> clearValue")
      Util.clear(outArr, 0, outArr.length)
    }

    protected def addToValue(winOff: Int, weight: Double): Unit = {
      val sz   = size
      val off1 = winOff * sz
      val b    = winBuf
      b.position(off1)
      val arr  = outArr
      var i = 0
      while (i < sz) {
        arr(i) += b.get() * weight
        i += 1
      }
    }

    protected def copyValueToOut(): Unit = {
      // debugLog("---> copyValueToOut")
      assert(/* !lockInToVal && !lockValToWin && */ !lockValToOut)
      Util.mul(outArr, 0, size, gain)
      lockValToOut = true
    }
  }
}