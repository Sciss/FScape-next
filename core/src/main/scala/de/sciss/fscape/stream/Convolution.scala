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
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.{Util, logStream => log}
import de.sciss.numbers.Implicits._
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

import scala.annotation.tailrec

object Convolution {
  var DEBUG_FORCE_FFT   = false // override auto-selection and always convolve in frequency domain
  var DEBUG_FORCE_TIME  = false // override auto-selection and always convolve in time domain

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

    private[this] var stage           = 0 // 0 -- needs kernel len, 1 -- needs in and/or kernel, 2 -- overlap-add and write

    private[this] var kernelLenReady  = false

    // this is set and reset by `InH` during `processFill`.
    // It may be checked between two calls to `InH.processFill`,
    // e.g. in `writeDone`.
    private[this] var updateKernel    = true
    private[this] var kernelDidFFT    = false
    private[this] var kernelLen       = 0
    private[this] var fftLen          = 0
    private[this] var fftCost         = 0   // = (fftLen * log2(fftLen)) * 3 + fftLen ; 2x FFT forward, 1 x multiplication, 1x backward
    private[this] var maxInLen        = 0

    private[this] var lapReadRem      = 0
    private[this] var lapWriteInOff   = 0   // wrt InH.array
    private[this] var lapWriteOutOff  = 0   // wrt lapBuf
    private[this] var lapWriteRem     = 0
    private[this] var lapReadOff      = 0   // wrt lapBuf

    private[this] var lapBuf: Array[Double] = _

    private[this] var outBuf: BufD = _
    private[this] var outOff = 0
    private[this] var outRem = 0
    private[this] var outFlush  = false

    private[this] var framesRead    = 0L
    private[this] var framesProd    = 0L
    private[this] var framesWritten = 0L

    private[this] var fft: DoubleFFT_1D = _
    private[this] var time: Array[Double] = _

    def onPull(): Unit = {
      val ok = stage == 2
      log(s"$this.out onPull() $ok")
      if (ok) {
        processOverlapAdd()
      }
    }

    private object InH extends InHandler {
      private[this] val in = shape.in0

      private[this] var arr     : Array[Double] = _
      private[this] var arrOff           = 0
      private[this] var arrRem           = 0

      private[this] var buf: BufD = _
      private[this] var bufOff  = 0
      private[this] var bufRem  = 0

      private[this] var _shouldFill   = false

      var isFilled = false

      def length: Int           = arrOff
      def array : Array[Double] = arr

//      def ended: Boolean = buf == null && (isClosed(in) && !isAvailable(in))

      override def toString: String = s"$logic.in"

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

        if (buf == null) {
          if      (isAvailable(in)) onPush()
          else if (isClosed   (in)) processDone()
        } else if (bufRem > 0) {
          processFill()
        }
      }

      def onPush(): Unit = {
        val ok = buf == null
        log(s"$this - onPush() $ok")
        if (ok) {
          buf     = grab(in)
          bufOff  = 0
          bufRem  = buf.size
          if (_shouldFill) {
            processFill()
          }
          tryPull(in)
        }
      }

      def kernelUpdateReady(): Unit =
        if (buf != null && _shouldFill) {
          processFill()
        }

      private def processFill(): Unit = {
        val len0  = math.min(bufRem, arrRem)
        val ku    = KernelUpdateH
        val len1  = ku.available(len0)
        if (len1 > 0) {
          // `takeValue` does not clear that flag,
          // and we need to ensure that `kernelUpdateReady`
          // is called when `KernelUpdateH` is closed.
          ku.clearHasValue()
          var len       = 0
          var _update   = false
          var isFirst   = updateKernel
          if (isFirst) updateKernel = false // clear it here
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
            bufOff  += len
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

        } else {
          // make sure `kernelUpdateReady` is called eventually
          if (ku.hasNext) {
            ku.next()
          }
        }

        processDone()
      }

      private def processDone(): Unit = {
        val ended = bufRem == 0 && isClosed(in) && !isAvailable(in)
        if (arrRem == 0 || ended) {
          _shouldFill = false
          arrRem      = 0
          Util.clear(arr, arrOff, arr.length - arrOff)
          isFilled = true
          if (arrOff == 0 && ended) {
            outFlush = true
          }
          notifyInFilled()
        }
      }

      override def onUpstreamFinish(): Unit = {
        val really = !isAvailable(in)
        log(s"$this - onUpstreamFinish() $really")
        if (really) {
          if (_shouldFill) processDone()
        }
      }

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

      var isFilled = false

      def length: Int           = arrOff
      def array : Array[Double] = arr

//      def ended: Boolean = buf == null && (isClosed(in) && !isAvailable(in))

      override def toString: String = s"$logic.kernel"

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

        if (buf == null) {
          if      (isAvailable(in)) onPush()
          else if (isClosed   (in)) processDone()
        } else if (bufRem > 0) {
          processFill()
        }
      }

      def onPush(): Unit = {
        val ok = buf == null
        log(s"$this - onPush() $ok")
        if (ok) {
          buf     = grab(in)
          bufOff  = 0
          bufRem  = buf.size
          if (_shouldFill) {
            processFill()
          }
          tryPull(in)
        }
      }

      private def processFill(): Unit = {
        val len = math.min(bufRem, arrRem)
        Util.copy(buf.buf, bufOff, arr, arrOff, len)
        bufOff  += len
        bufRem  -= len
        arrOff  += len
        arrRem  -= len
        if (bufRem == 0) {
          buf.release()
          buf = null
        }
        processDone()
      }

      private def processDone(): Unit = {
        if (arrRem == 0 || (isClosed(in) && !isAvailable(in))) {
          _shouldFill = false
          arrRem      = 0
          Util.clear(arr, arrOff, arr.length - arrOff)
          isFilled = true
          notifyKernelFilled()
        }
      }

      override def onUpstreamFinish(): Unit = {
        val really = !isAvailable(in)
        log(s"$this - onUpstreamFinish() $really")
        if (really) {
          if (_shouldFill) processDone()
        }
      }

      setHandler(in, this)
    }

    private object KernelLenH extends InHandlerImpl[Int, BufI](shape.in2) {
      protected def notifyValue(): Unit = notifyKernelLenReady()
    }

    private object KernelUpdateH extends InHandlerImpl[Int, BufI](shape.in3) {
      protected def notifyValue(): Unit = InH.kernelUpdateReady()
    }

    InH
    KernelH
    KernelLenH
    KernelUpdateH
    setHandler(shape.out, this)

    override protected def stopped(): Unit = {
      fft     = null
      time    = null
      lapBuf  = null
      if (outBuf != null) {
        outBuf.release()
        outBuf = null
      }
      InH           .freeBuffer()
      KernelH       .freeBuffer()
      KernelLenH    .freeBuffer()
      KernelUpdateH .freeBuffer()
    }

    private def writeDone(): Unit =
      if (outFlush) {
        if (framesWritten == framesProd && outOff == 0) {
          completeStage()
        }
      } else {
        if (updateKernel) {
          stage = 0
          if (kernelLenReady) {
            kernelLenReady = false
            processKernelLen()
          } else {
            KernelLenH.next()
          }

        } else {
          stage = 1
          InH.isFilled = false
          InH.shouldFill()
        }
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
          fft   = null
          time  = null
          var _fftLog = 1
          var _fftLen1 = _fftLen
          while (_fftLen1 > 2) {
            _fftLog += 1
            _fftLen1 >>>= 1
          }
          fftCost =
            if      (DEBUG_FORCE_FFT  ) 0
            else if (DEBUG_FORCE_TIME ) Int.MaxValue
            else (_fftLen * _fftLog) * 3 + _fftLen
        }
      }
      stage         = 1
      kernelDidFFT  = false

      // N.B.: It's important to set these flags to
      // false first, because `KernelH.shouldFill()`
      // may end up completing and asking `InH.isFilled`!
      InH     .isFilled = false
      KernelH .isFilled = false
      KernelH .shouldFill()
      InH     .shouldFill()
    }

    private def notifyKernelFilled(): Unit = {
      val ok = InH.isFilled
      log(s"$this - notifyKernelFilled() $ok")
      if (ok) processConvolution()
    }

    private def notifyInFilled(): Unit = {
      val ok = KernelH.isFilled
      log(s"$this - notifyInFilled() $ok")
      if (ok) processConvolution()
    }

    private def processConvolution(): Unit = {
      val _inLen = InH.length
      if (_inLen > 0) {
        val _kernelLen  = kernelLen
        val _inArr      = InH     .array
        val _kernelArr  = KernelH .array
        val _fftLen     = fftLen
        val _convLen    = _inLen + _kernelLen - 1

        if (!kernelDidFFT && /* timeCost */ _inLen * _kernelLen <= fftCost) {  // perform convolution in time domain

          if (time == null) {
            time = new Array[Double](_fftLen)
          } else {
            Util.clear(time, 0, _convLen)
          }
          val _time = time

          var n = 0
          while (n < _convLen) {
            var nm  = n
            var m   = 0
            var sum = 0.0
            while (nm >= 0 && m < _inLen) {
              sum += _inArr(m) * _kernelArr(nm)
              m   += 1
              nm  -= 1
            }
            _time(n) = sum
            n += 1
          }
          Util.copy(_time, 0, _inArr, 0, _convLen)

        } else {  // perform convolution in frequency domain
          if (fft == null) {
            fft = new DoubleFFT_1D(_fftLen)
          }
          if (!kernelDidFFT) {
            fft.realForward(_kernelArr)
            kernelDidFFT = true
          }
          fft.realForward(_inArr)
          // N.B.: We use compact storage. Since
          // the components at DC and Nyquist are purely
          // real, there is no imaginary component
          // for DC, instead at offset 1 we find the
          // real component of Nyquist. So we need
          // to special-case the multiplication for the
          // first two array indices.
          _inArr(0) *= _kernelArr(0)  // real multiplication for DC
          _inArr(1) *= _kernelArr(1)  // real multiplication for Nyquist
          var idxRe = 2
          while (idxRe < _fftLen) {
            val aRe       = _inArr    (idxRe)
            val bRe       = _kernelArr(idxRe)
            val idxIm     = idxRe + 1
            val aIm       = _inArr    (idxIm)
            val bIm       = _kernelArr(idxIm)
            _inArr(idxRe) = aRe * bRe - aIm * bIm
            _inArr(idxIm) = aRe * bIm + aIm * bRe
            idxRe += 2
          }
          fft.realInverse(_inArr, /* scale = */ true)
        }

        lapWriteRem   = _convLen
        val prod0     = framesRead + _convLen
        framesRead   += _inLen
        framesProd    = math.max(framesProd, prod0) // kernel may shrink, maintain the maximum production length
      }

      stage         = 2
      lapWriteInOff = 0
      processOverlapAdd()
    }

    @tailrec
    private def processOverlapAdd(): Unit = {
      var stateChanged = false

      /*

          - the convolution tail has length `kernelLen - 1`.
          - the amount we can advance output is `inLen`.

       */

      if (lapReadRem == 0) {
        if (lapBuf == null) {
          lapBuf        = new Array[Double](fftLen)

        } else if (lapBuf.length < fftLen) {  // grow overlap buffer
          val oldLapBuf   = lapBuf
          lapBuf          = new Array[Double](fftLen)
          val chunkGrow1  = oldLapBuf.length - lapReadOff
          Util.copy(oldLapBuf, lapReadOff, lapBuf, 0, chunkGrow1)
          if (lapReadOff > 0) {
            Util.copy(oldLapBuf, 0, lapBuf, chunkGrow1, lapReadOff)
            lapWriteOutOff  = (lapWriteOutOff - lapReadOff + lapBuf.length) % lapBuf.length
            lapReadOff      = 0
          }
          stateChanged  = true
        }

        val _inLen      = InH.length
        val chunkWrite1 = math.min(lapBuf.length - lapWriteOutOff, lapWriteRem)
        if (chunkWrite1 > 0) {
          Util.add(InH.array, lapWriteInOff, lapBuf, lapWriteOutOff, chunkWrite1)
          val writeOff1  = (lapWriteOutOff + chunkWrite1) % lapBuf.length
          lapWriteInOff += chunkWrite1
          lapWriteRem   -= chunkWrite1
          if (lapWriteRem > 0) {
            Util.add(InH.array, lapWriteInOff, lapBuf, writeOff1, lapWriteRem)
            lapWriteInOff += lapWriteRem
            lapWriteRem    = 0
          }
          // we advance the write offset in the lap buffer by inLen,
          // which corresponds with the amount of frames to be flushed
          // to out (lapReadRem)
          lapWriteOutOff  = (lapWriteOutOff + _inLen) % lapBuf.length
          stateChanged    = true
        }

        if (_inLen > 0) {
          lapReadRem    = _inLen
          stateChanged  = true
        } else if (outFlush) {
          val _inLen1   = (framesProd - framesWritten).toInt
          lapReadRem    = _inLen1
          if (_inLen1 > 0) stateChanged = true
        }
      }

      if (outBuf == null) {
        outBuf        = ctrl.borrowBufD()
        outOff        = 0
        outRem        = outBuf.size
        stateChanged  = true
      }
      if (outFlush) {
        outRem = math.min(outRem, lapReadRem)
      }

      val chunkRead1 = math.min(lapReadRem, outRem)
      if (chunkRead1 > 0) {
        val chunkRead2 = math.min(lapBuf.length - lapReadOff, chunkRead1)
        Util.copy (lapBuf, lapReadOff, outBuf.buf, outOff, chunkRead2)
        Util.clear(lapBuf, lapReadOff, chunkRead2)
        lapReadOff     = (lapReadOff + chunkRead2) % lapBuf.length
        outOff        += chunkRead2
        outRem        -= chunkRead2
        lapReadRem    -= chunkRead2
        framesWritten += chunkRead2
        val chunkRead3 = chunkRead1 - chunkRead2
        if (chunkRead3 > 0) {
          Util.copy (lapBuf, lapReadOff, outBuf.buf, outOff, chunkRead3)
          Util.clear(lapBuf, lapReadOff, chunkRead3)
          lapReadOff     = (lapReadOff + chunkRead3) % lapBuf.length
          outOff        += chunkRead3
          outRem        -= chunkRead3
          lapReadRem    -= chunkRead3
          framesWritten += chunkRead3
        }

        stateChanged = true
      }

      if (outRem == 0 && isAvailable(shape.out)) {
        if (outOff > 0) {
          outBuf.size   = outOff
          outOff = 0  // important, see check in `writeDone`
          push(shape.out, outBuf)
        } else {
          outBuf.release()
        }
        outBuf        = null
        stateChanged  = true
      }

      if (lapReadRem == 0 && lapWriteRem == 0) {
        writeDone()
      } else {
        if (stateChanged) processOverlapAdd()
      }
    }
  }
}