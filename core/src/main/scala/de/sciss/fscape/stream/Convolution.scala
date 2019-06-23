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
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

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

    private[this] var kernelLenReady  = false

    private[this] var updateKernel    = true
    private[this] var kernelDidFFT    = false
    private[this] var kernelLen       = 0
    private[this] var fftLen          = 0
    private[this] var fftCost         = 0   // = (fftLen * log2(fftLen)) * 3 + fftLen ; 2x FFT forward, 1 x multiplication, 1x backward
    private[this] var maxInLen        = 0

    private[this] var fft: DoubleFFT_1D = _

    def onPull(): Unit = ???

    private object InH extends InHandler {
      private[this] val in = shape.in0

      private[this] var arr     : Array[Double] = _
      private[this] var arrOff           = 0
      private[this] var arrRem           = 0

      private[this] var buf: BufD = _
      private[this] var bufOff  = 0
      private[this] var bufRem  = 0

      private[this] var _shouldFill   = false

      def isFilled: Boolean = !_shouldFill

      def length: Int           = arrOff
      def array : Array[Double] = arr

      def freeBuffer(): Unit = {
        if (buf != null) {
          buf.release()
          buf = null
        }
        arr = null
      }

      def shouldFill(): Unit = if (!_shouldFill) {
        _shouldFill = true
        if (arr == null || fftLen != arr.length) {
          arr = new Array[Double](fftLen)
        }
        arrOff  = 0
        arrRem  = maxInLen

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
              Util.clear(arr, arrOff, arr.length - arrOff)
              notifyInFilled()
            }
          }
          tryPull(in)
        }
      }

      private def processFill(): Unit = {
        val len0  = math.min(bufRem, arrRem)
        val ku    = KernelUpdateH
        val len1  = ku.available(len0)
        if (len1 > 0) {
          var len       = 0
          var _update   = false
          var isFirst   = arrOff == 0
          while ({
            len < len1 && {
              _update = if (isFirst) {
                isFirst = false
                false
              } else {
                ku.takeValue() != 0
              }
              !_update
            }
          }) {
            len += 1
          }

          if (len > 0) {
            Util.copy(buf.buf, bufOff, arr, arrOff, len)
            bufRem  -= len
            arrOff  += len
            arrRem  -= len
            if (bufRem == 0) {
              buf.release()
              buf = null
            }
          }

          if (_update) {
            updateKernel  = true
            arrRem        = 0
          }
        }
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

      def isFilled: Boolean = !_shouldFill

      def length: Int           = arrOff
      def array : Array[Double] = arr

      def freeBuffer(): Unit = {
        if (buf != null) {
          buf.release()
          buf = null
        }
        arr = null
      }

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
              Util.clear(arr, arrOff, arr.length - arrOff)
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

    override protected def stopped(): Unit = {
      fft = null
      InH           .freeBuffer()
      KernelH       .freeBuffer()
      KernelLenH    .freeBuffer()
      KernelUpdateH .freeBuffer()
    }

    private def notifyKernelLenReady(): Unit = {
      assert (!kernelLenReady)
      if (stage == 0) {
        processKernelLen()
      } else {
        kernelLenReady = true
      }
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
        val _fftLen  = if (r0 < r1) fftLen0 else fftLen1  // choose the more balanced ratio of input and kernel len
        val oldFFTLen = fftLen
        fftLen      = _fftLen
        maxInLen    = _fftLen - _kernelLen + 1
        if (_fftLen != oldFFTLen) {
          fft = null
          var _fftLog = 1
          var _fftLen1 = _fftLen
          while (_fftLen1 > 2) {
            _fftLog += 1
            _fftLen1 >>>= 1
          }
          fftCost = (_fftLen * _fftLog) * 3 + _fftLen
        }
      }
      stage         = 1
      updateKernel  = false // will again be set to `true` by `InH`
      kernelDidFFT  = false
      KernelH .shouldFill()
      InH     .shouldFill()
    }

    private def notifyKernelFilled(): Unit =
      if (InH.isFilled) processConvolution()

    private def notifyInFilled(): Unit =
      if (KernelH.isFilled) processConvolution()

    private def processConvolution(): Unit = {
      stage = 2
      val _inLen      = InH.length
      val _kernelLen  = kernelLen
      val _inArr      = InH     .array
      val _kernelArr  = KernelH .array

      if (!kernelDidFFT && /* timeCost */ _inLen * _kernelLen <= fftCost) {  // perform convolution in time domain
        var i = 0
        while (i < _inLen) {
          var sum = 0.0
          val iv = _inArr(i)
          var j = 0
          while (j < _kernelLen) {
            sum += iv * _kernelArr(j)
            j += 1
          }
          _inArr(i) = sum
          i += 1
        }

      } else {  // perform convolution in frequency domain
        val _fftLen = fftLen
        if (fft == null) {
          fft = new DoubleFFT_1D(_fftLen)
        }
        if (!kernelDidFFT) {
          fft.realForward(_kernelArr)
          kernelDidFFT = true
        }
        fft.realForward(_inArr)
        var idxRe = 0
        while (idxRe < _fftLen) {
          val aRe = _inArr    (idxRe)
          val bRe = _kernelArr(idxRe)
          val idxIm = idxRe + 1
          val aIm = _inArr    (idxIm)
          val bIm = _kernelArr(idxIm)
          _inArr(idxRe) = aRe * bRe - aIm * bIm
          _inArr(idxIm) = aRe * bIm + aIm * bRe
          idxRe += 2
        }
        fft.realInverse(_inArr, /* scale = */ true)
      }

      ???
    }
  }
}