/*
 *  ConstQ.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.graph.GenWindow.Hamming
import de.sciss.fscape.stream.impl.{FilterIn5DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}
import de.sciss.numbers
import de.sciss.numbers.IntFunctions
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

object ConstQ {
  def apply(in: OutD, fftSize: OutI, minFreqN: OutD, maxFreqN: OutD, numBands: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in      , stage.in0)
    b.connect(fftSize , stage.in1)
    b.connect(minFreqN, stage.in2)
    b.connect(maxFreqN, stage.in3)
    b.connect(numBands, stage.in4)
    stage.out
  }

  private final val name = "ConstQ"

  private type Shp = FanInShape5[BufD, BufI, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape5(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.fftSize"  ),
      in2 = InD (s"$name.minFreqN" ),
      in3 = InD (s"$name.maxFreqN" ),
      in4 = InI (s"$name.zero"     ),
      out = OutD(s"$name.out"      )
    )
    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // N.B.: `offset` is "flat complex", so always even
  private final class Kernel(val offset: Int, val data: Array[Double], val freqN: Double)

  // XXX TODO --- we could store pre-calculated cosine tables for
  // sufficiently small table sizes
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterLogicImpl[BufD, Shp]
      with WindowedLogicImpl[Shp]
      with FilterIn5DImpl[BufD, BufI, BufD, BufD, BufI] {

    private[this] var size      = 0
    private[this] var minFreqN  = -1d
    private[this] var maxFreqN  = -1d
    private[this] var numBands  = 0

    private[this] var inBuf   : Array[Double] = _
    private[this] var outBuf  : Array[Double] = _
    private[this] var kernels : Array[Kernel] = _

    override protected def stopped(): Unit = {
      super.stopped()
      inBuf  = null
      outBuf = null
    }

    protected def startNextWindow(inOff: Int): Long = {
      var needsUpdate = false
      if (bufIn1 != null && inOff < bufIn1.size) {
        val _size = math.max(1, bufIn1.buf(inOff))
        if (size != _size) {
          size        = _size
          needsUpdate = true
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val _minFreqN = math.max(1.0e-7, math.min(0.5d, bufIn2.buf(inOff)))
        if (minFreqN != _minFreqN) {
          minFreqN    = _minFreqN
          needsUpdate = true
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        val _maxFreqN = math.max(1.0e-7, math.min(0.5d, bufIn3.buf(inOff)))
        if (maxFreqN != _maxFreqN) {
          maxFreqN    = _maxFreqN
          needsUpdate = true
        }
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        val _numBands = math.max(1, bufIn4.buf(inOff))
        if (numBands != _numBands) {
          numBands    = _numBands
          needsUpdate = true
        }
      }
      if (needsUpdate) {
        updateConfig()
      }
      size
    }

    // mostly copied from ScissDSP's ConstQ
    private def updateConfig(): Unit = {
      val reverse     = maxFreqN < minFreqN
      if (reverse) {
        val tmp   = minFreqN
        minFreqN  = maxFreqN
        maxFreqN  = tmp
      }
      val freqF       = maxFreqN/minFreqN
      val _numBands   = numBands
      val q           = 1.0 / (math.pow(freqF, 1.0 / _numBands) - 1.0)
      val _kernels    = new Array[Kernel](_numBands)
      val maxKernLen  = q / minFreqN // config.sampleRate / config.minFreq
      val maxKernLenI = math.ceil(maxKernLen).toInt
      val win         = new Array[Double](maxKernLenI)
      inBuf           = new Array[Double](size)
      outBuf          = new Array[Double](_numBands)

      val fftSizeK    = math.min(size, IntFunctions.nextPowerOfTwo(maxKernLenI))
      val fftSizeC    = fftSizeK << 1
      val fftBuf      = new Array[Double](fftSizeC)


      val fft         = new DoubleFFT_1D(fftSizeK)

      //		thresh		= 0.0054f / fftLen; // for Hamming window
      // weird observation : lowering the threshold will _increase_ the
      // spectral noise, not improve analysis! so the truncating of the
      // kernel is essential for a clean analysis (why??). the 0.0054
      // seems to be a good choice, so don't touch it.
      //		threshSqr	= 2.916e-05f / (fftSize * fftSize); // for Hamming window (squared!)
      //		threshSqr	= 2.916e-05f / fftSize; // for Hamming window (squared!)
      // (0.0054 * 3).squared
//      val threshSqr = 2.6244e-4 / (fftSizeK * fftSizeK) // for Hamming window (squared!)
      val threshSqr = 2.6244e-4

      var lastKernLen = -1

      var k = 0; while (k < _numBands) {
        val freqK         = math.pow(freqF, k.toDouble / _numBands)
        val kernLenIdeal  = maxKernLen / freqK
        val kernelLen     = math.min(fftSizeK, math.ceil(kernLenIdeal).toInt)
        val kernelLenE    = kernelLen & ~1
        if (kernelLen != lastKernLen) {
          // `param` is unused by Hamming
          Hamming.fill(winSize = kernelLen, winOff = 0, buf = win, bufOff = 0, len = kernelLen, param = 0.0)
          lastKernLen = kernelLen
        }

        val centerFreqN   = minFreqN * freqK
        val centerFreqRad = centerFreqN * -numbers.TwoPi

        // this is a good approximation in the kernel len truncation case
        // (tested with pink noise, where it will appear pretty much
        // with the same brightness over all frequencies)
        val weight        = 6 / ((kernLenIdeal + kernelLen) * fftSizeK)

        var m = kernelLenE; val n = fftSizeC - kernelLenE; while (m < n) {
          fftBuf(m) = 0f
          m += 1 }

        // note that we calculate the complex conjugation of
        // the temporal kernel and reverse its time, so the resulting
        // FFT can be immediately used for the convolution and does not
        // need to be conjugated; this is due to the Fourier property
        // h*(-x) <-> H*(f). time reversal is accomplished by
        // having iteration variable j run....

        var i = kernelLen - 1; var j = fftSizeC - kernelLenE; while (i >= 0) {
          // complex exponential of a purely imaginary number
          // is cos(imag(n)) + i sin(imag(n))
          val d1    = centerFreqRad * i
          val cos   = math.cos(d1)
          val sin   = math.sin(d1)
          val d2    = win(i) * weight
          fftBuf(j) = d2 * cos; j += 1
          fftBuf(j) = d2 * sin; j += 1 // NORM!
          if (j == fftSizeC) j = 0
          i -= 1 }

        // XXX to be honest, i don't get the point
        // of calculating the fft here, since we
        // have an analytic description of the kernel
        // function, it should be possible to calculate
        // the spectral coefficients directly
        // (the fft of a hamming is a gaussian,
        // isn't it?)

        fft.complexForward(fftBuf)
        Util.mul(fftBuf, 0, fftSizeK, fftSizeK)

        // with a "high" threshold like 0.0054, the
        // point is _not_ to create a sparse matrix by
        // gating the values. in fact we can locate
        // the kernel spectrally, so all we need to do
        // is to find the lower and upper frequency
        // of the transformed kernel! that makes things
        // a lot easier anyway since we don't need
        // to employ a special sparse matrix library.

        val specStart = {
          var i = 0; var break = false; while (!break && i <= fftSizeK) {
            val f1      = fftBuf(i)
            val f2      = fftBuf(i + 1)
            val magSqr  = f1 * f1 + f2 * f2
            if (magSqr > threshSqr) break = true else i += 2
          }
          i
        }

        // final matrix product:
        // input chunk (fft'ed) is a row vector with n = fftSize
        // kernel is a matrix mxn with m = fftSize, n = numKernels
        // result is a row vector with = numKernels
        // ; note that since the input is real and hence we
        // calculate only the positive frequencies (up to pi),
        // we might need to mirror the input spectrum to the
        // negative frequencies. however it can be observed that
        // for practically all kernels their frequency bounds
        // lie in the positive side of the spectrum (only the
        // high frequencies near nyquist blur across the pi boundary,
        // and we will cut the overlap off by limiting specStop
        // to fftSize instead of fftSize<<1 ...).

        val specStop = {
          var i = specStart; var break = false; while (!break && i <= fftSizeK) {
            val f1      = fftBuf(i)
            val f2      = fftBuf(i + 1)
            val magSqr  = f1 * f1 + f2 * f2
            if (magSqr <= threshSqr) break = true else i += 2
          }
          i
        }

        _kernels(k) = new Kernel(specStart, new Array[Double](specStop - specStart), centerFreqN)
        System.arraycopy(fftBuf, specStart, _kernels(k).data, 0, specStop - specStart)

        k += 1
      }

      if (reverse) {
        Util.reverse(_kernels, 0, _numBands)
      }
      kernels = _kernels
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, inBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(outBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val _bufIn      = inBuf
      val _bufOut     = outBuf
      val inStop      = writeToWinOff.toInt & ~1
      val _numBands   = numBands

      var k = 0; var outOff = 0; while (k < _numBands) {
        val kern  = kernels(k)
        val data  = kern.data
        var f1    = 0d
        var f2    = 0d
        var i     = kern.offset
        var j     = 0
        val n     = math.min(data.length, inStop - i)
        while (j < n) {
          // complex multiplication: a * b =
          // (re(a)re(b)-im(a)im(b))+i(re(a)im(b)+im(a)re(b))
          // ; since we left out the conjugation of the kernel(!!)
          // this becomes (assume a is input and b is kernel):
          // (re(a)re(b)+im(a)im(b))+i(im(a)re(b)-re(a)im(b))
          // ; in fact this conjugation is unimportant for the
          // calculation of the magnitudes...
          val re1 = _bufIn(i)
          val im1 = _bufIn(i + 1)
          val re2 = data(j)
          val im2 = data(j + 1)
          f1 += re1 * re2 - im1 * im2
          f2 += re1 * im2 + im1 * re2
          i += 2; j += 2
        }

        // since we use constQ to decimate spectra, we actually
        // are going to store a "mean square" of the amplitudes
        // so at the end of the decimation we'll have one square root,
        // or even easier, when calculating the logarithm, the
        // multiplier of the log just has to be divided by two.
        // hence we don't calc abs( f ) but abs( f )^2 !

        _bufOut(outOff) = f1 * f1 + f2 * f2

        k += 1; outOff += 1
      }

      _numBands
    }
  }
}