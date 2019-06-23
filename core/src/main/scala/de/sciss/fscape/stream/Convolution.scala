/*
 *  Convolution.scala
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

package de.sciss.fscape.stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

object Convolution {
  def apply(in: OutD, kernel: OutD, kernelLen: OutI, kernelUpdate: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in          , stage.in0)
    b.connect(kernel      , stage.in1)
    b.connect(kernelLen   , stage.in2)
    b.connect(kernelUpdate, stage.in3)
    stage.out
  }

  private final val name = "Convolution"

  private type Shape = FanInShape4[BufD, BufD, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"          ),
      in1 = InD (s"$name.kernel"      ),
      in2 = InI (s"$name.kernelLen"   ),
      in3 = InI (s"$name.kernelUpdate"),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with OutHandler { logic =>

    private[this] var stage           = 0 // 0 -- needs kernel len, 1 -- needs in and/or kernel

    private[this] var inReady         = false
    private[this] var kernelLenReady  = false

    private[this] var kernelLen       = 0
    private[this] var fftLen          = 0
    private[this] var maxInLen        = 0

    private[this] var inBuf     : Array[Double] = _
    private[this] var inOff           = 0
    private[this] var inRem           = 0

    def onPull(): Unit = ???

    private object InH extends InHandler {
      private[this] val in = shape.in0

      var buf: BufD = _
      var bufOff  = 0
      var bufRem  = 0

      def onPush(): Unit = {
        if (buf == null) {
          buf     = grab(in)
          bufOff  = 0
          bufRem  = buf.size
          notifyInReady()
          tryPull(in)
        }
      }

      def ping(): Unit =
        if (isAvailable(in)) onPush()

      def process(): Boolean = {
        val len = math.min(bufRem, inRem)
        Util.copy(buf.buf, bufOff, inBuf, inOff, len)
        bufRem  -= len
        inOff   += len
        inRem   -= len
        if (bufRem == 0) {
          buf.release()
          buf = null
        }
        inRem == 0
      }

      override def onUpstreamFinish(): Unit = ???

      setHandler(in, this)
    }

    private object KernelH extends InHandler {
      private[this] val in = shape.in1

      private[this] var arr: Array[Double] = _
      private[this] var arrOff    = 0
      private[this] var arrRem    = 0

      private[this] var buf: BufD = _
      private[this] var bufOff  = 0
      private[this] var bufRem  = 0

      private[this] var _shouldFill = false

      def shouldFill(): Unit = if (!_shouldFill) {
        _shouldFill = true
        if (arr == null || fftLen != arr.length) {
          arr = new Array[Double](fftLen)
        }
        arrOff  = 0
        arrRem  = kernelLen

        if (isAvailable(in)) onPush()
      }

      def onPush(): Unit = {
        if (buf == null) {
          buf     = grab(in)
          bufOff  = 0
          bufRem  = buf.size
          if (_shouldFill) {
            processFill()
            if (arrRem == 0) {
              _shouldFill = false
              notifyKernelFilled()
            }
          }
          tryPull(in)
        }
      }

      private def processFill(): Unit = {
        val len = math.min(bufRem, arrRem)
        Util.copy(buf.buf, bufOff, arr, arrOff, len)
        bufRem  -= len
        arrOff  += len
        arrRem  -= len
        if (bufRem == 0) {
          buf.release()
          buf = null
        }
      }

      override def onUpstreamFinish(): Unit = ???

      setHandler(in, this)
    }

    private object KernelLenH extends InHandlerImpl[Int, BufI](shape.in2) {
      protected def notifyValue(): Unit = notifyKernelLenReady()
    }

    private object KernelUpdateH extends InHandlerImpl[Int, BufI](shape.in3) {
      protected def notifyValue(): Unit = ???
    }

    InH
    KernelH
    KernelLenH
    KernelUpdateH
    setHandler(shape.out, this)

    private def notifyKernelLenReady(): Unit = {
      assert (!kernelLenReady)
      if (stage == 0) {
        processKernelLen()
      } else {
        kernelLenReady = true
      }
    }

    private def notifyKernelFilled(): Unit = {
      ???
//      assert (!kernelReady)
//      if (stage == 1) {
//        processKernel()
//      } else {
//        kernelReady = true
//      }
    }

    private def processKernelLen(): Unit = {
      val oldKernelLen = kernelLen
      val _kernelLen = math.max(1, KernelLenH.takeValue())
      if (_kernelLen != oldKernelLen) {
        kernelLen   = _kernelLen
        val fftLen0 = (_kernelLen + 1).nextPowerOfTwo
        val inLen0  = fftLen0 - _kernelLen + 1
        val fftLen1 = fftLen0 << 1
        val inLen1  = inLen0 + fftLen0
        val r0      = if (inLen0 <= _kernelLen) _kernelLen.toDouble / inLen0 else inLen0.toDouble / _kernelLen
        val r1      = inLen1.toDouble / _kernelLen
        val oldFFTLen = fftLen
        val _fftLen  = if (r0 < r1) fftLen0 else fftLen1  // choose the more balanced ratio of input and kernel len
        fftLen      = _fftLen
        maxInLen    = _fftLen - _kernelLen + 1
        if (_fftLen != oldFFTLen) {
          inBuf     = new Array[Double](_fftLen)
        }
      }
      stage = 1
      KernelH.shouldFill()
    }

    private def notifyInReady(): Unit = {
      assert (!inReady)
      if (stage == 1) {
        processIn()
      } else {
        inReady = true
      }
    }

    private def processIn(): Unit = {
      val done = InH.process()
      if (done) {
        stage = 2


      } else {
        InH.ping()
      }
    }
  }
}