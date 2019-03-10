/*
 *  MelFilter.scala
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

import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.{FilterIn6DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object MelFilter {
  def apply(in: OutD, size: OutI, minFreq: OutD, maxFreq: OutD, sampleRate: OutD, bands: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(minFreq   , stage.in2)
    b.connect(maxFreq   , stage.in3)
    b.connect(sampleRate, stage.in4)
    b.connect(bands     , stage.in5)
    stage.out
  }

  private final val name = "MelFilter"

  private type Shape = FanInShape6[BufD, BufI, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.size"      ),
      in2 = InD (s"$name.minFreq"   ),
      in3 = InD (s"$name.maxFreq"   ),
      in4 = InD (s"$name.sampleRate"),
      in5 = InI (s"$name.bands"     ),
      out = OutD(s"$name.out"       )
    )
    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterLogicImpl[BufD, Shape]
      with WindowedLogicImpl[Shape]
      with FilterIn6DImpl[BufD, BufI, BufD, BufD, BufD, BufI] {

    private[this] var magSize     = 0
    private[this] var minFreq     = 0.0
    private[this] var maxFreq     = 0.0
    private[this] var sampleRate  = 0.0
    private[this] var bands       = 0

    private[this] var magBuf    : Array[Double] = _
    private[this] var melBuf    : Array[Double] = _
    private[this] var binIndices: Array[Int   ] = _

    override protected def stopped(): Unit = {
      super.stopped()
      magBuf = null
    }

    protected def startNextWindow(inOff: Int): Long = {
      var updatedBins = false
      if (bufIn1 != null && inOff < bufIn1.size) {
        val _magSize = math.max(1, bufIn1.buf(inOff))
        if (magSize != _magSize) {
          magSize     = _magSize
          updatedBins = true
        }
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        val _sampleRate = bufIn4.buf(inOff)
        if (sampleRate != _sampleRate) {
          sampleRate  = _sampleRate
          updatedBins = true
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val nyquist   = sampleRate/2
        val ceil      = (magSize - 1).toDouble / magSize * nyquist
        val _minFreq  = math.max(0, math.min(ceil, bufIn2.buf(inOff)))
        if (minFreq != _minFreq) {
          minFreq     = _minFreq
          updatedBins = true
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        val nyquist   = sampleRate / 2
        val ceil      = (magSize - 1).toDouble / magSize * nyquist
        val _maxFreq  = math.max(minFreq, math.min(ceil, bufIn3.buf(inOff)))
        if (maxFreq != _maxFreq) {
          maxFreq     = _maxFreq
          updatedBins = true
        }
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        val _bands = math.max(1, bufIn5.buf(inOff))
        if (bands != _bands) {
          bands     = _bands
          updatedBins = true
        }
      }
      if (updatedBins) {
        magBuf      = new Array[Double](magSize)
        melBuf      = new Array[Double](bands + 2) // we need two samples more internally
        binIndices  = calcBinIndices()
      }
      magSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, magBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      // add `+ 1` because we skip that first value
      Util.copy(melBuf, readFromWinOff.toInt + 1, bufOut0.buf, outOff, chunk)
    }

    private def melToFreq(mel : Double): Double =  700 * (math.pow(10, mel / 2595) - 1)
    private def freqToMel(freq: Double): Double = 2595 * math.log10(1 + freq / 700)

    private def calcBinIndices(): Array[Int] = {
      val melFLow   = freqToMel(minFreq)
      val melFHigh  = freqToMel(maxFreq)
      val _bands    = bands

      def centerFreq(i: Int): Double = {
        val temp = melFLow + ((melFHigh - melFLow) / (_bands + 1)) * i
        melToFreq(temp)
      }

      val _magSize  = magSize
      val r         = (_magSize * 2) / sampleRate
      val _binIdx   = new Array[Int](_bands + 2)
      var i = 0
      while (i < _binIdx.length) {
        val fc  = centerFreq(i)
        val j   = math.round(fc * r).toInt
        if (j > _magSize) throw new IllegalArgumentException(s"Frequency $fc exceed Nyquist")
        _binIdx(i) = j
        i += 1
      }
      _binIdx
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val _bands  = bands
      val _melBuf = melBuf
      val _binIdx = binIndices
      val _magBuf = magBuf
      var k = 1
      while (k <= _bands) {
        val p = _binIdx(k - 1)
        val q = _binIdx(k)
        val r = _binIdx(k + 1)
        var i = p
        val s0 = (i - p + 1) / (q - p + 1) // should this be floating point?
        var num = 0.0
        while (i <= q) {
          num += s0 * _magBuf(i)
          i += 1
        }

        i = q + 1
        val s1 = 1 - ((i - q) / (r - q + 1)) // should this be floating point?
        while (i <= r) {
          num += s1 * _magBuf(i)
          i += 1
        }

        _melBuf(k) = num
        k += 1
      }
      bands
    }
  }
}