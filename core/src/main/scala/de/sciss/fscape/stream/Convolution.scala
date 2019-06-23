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

    private[this] var stage           = 0 // 0 -- needs kernel len, 1 -- needs kernel
    private[this] var kernelReady     = false
    private[this] var kernelLenReady  = false
    private[this] var kernelLen       = 0
    private[this] var fftLen          = 0
    private[this] var maxInLen        = 0

    private[this] var inBuf     : Array[Double] = _
    private[this] var kernelBuf : Array[Double] = _
    private[this] var kernelOff       = 0
    private[this] var kernelRem       = 0

    def onPull(): Unit = ???

    private object InH extends InHandler {
      private[this] val in = shape.in0

      def onPush(): Unit = ???

      override def onUpstreamFinish(): Unit = ???

      setHandler(in, this)
    }

    private object KernelH extends InHandler {
      private[this] val in = shape.in1

      var buf: BufD = _
      var bufOff  = 0
      var bufRem  = 0

      def onPush(): Unit = {
        if (buf == null) {
          buf     = grab(in)
          bufOff  = 0
          bufRem  = buf.size
          notifyKernelReady()
          tryPull(in)
        }
      }

      def process(): Boolean = {
        val len = math.min(bufRem, kernelRem)
        Util.copy(buf.buf, bufOff, kernelBuf, kernelOff, len)
        bufRem    -= len
        kernelOff += len
        kernelRem -= len
        if (bufRem == 0) {
          buf.release()
          buf = null
        }
        kernelRem == 0
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

    private def notifyKernelReady(): Unit = {
      assert (!kernelReady)
      if (stage == 1) {
        processKernel()
      } else {
        kernelReady = true
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
        val oldFFTLen = fftLen
        val _fftLen  = if (r0 < r1) fftLen0 else fftLen1
        fftLen      = _fftLen
        maxInLen    = _fftLen - _kernelLen + 1
        if (_fftLen != oldFFTLen) {
          inBuf     = new Array[Double](_fftLen)
          kernelBuf = new Array[Double](_fftLen)
        }
      }
      stage = 1
      kernelRem = _kernelLen
      if (kernelReady) {
        processKernel()
      }
    }

    private def processKernel(): Unit = {
      val done = KernelH.process()
      ???
    }
  }
}