/*
 *  MelFilter.scala
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

import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl, WindowedLogicD}
import de.sciss.numbers.Implicits._

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

  private type Shp = FanInShape6[BufD, BufI, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape6(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.size"      ),
      in2 = InD (s"$name.minFreq"   ),
      in3 = InD (s"$name.maxFreq"   ),
      in4 = InD (s"$name.sampleRate"),
      in5 = InI (s"$name.bands"     ),
      out = OutD(s"$name.out"       )
    )
    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedLogicD {

    override protected  val hIn     : InDMain   = InDMain  (this, shape.in0)
    override protected  val hOut    : OutDMain  = OutDMain (this, shape.out)
    private[this]       val hSize   : InIAux    = InIAux   (this, shape.in1)(math.max(1, _))
    private[this]       val hLoFreq : InDAux    = InDAux   (this, shape.in2)()
    private[this]       val hHiFreq : InDAux    = InDAux   (this, shape.in3)()
    private[this]       val hSR     : InDAux    = InDAux   (this, shape.in4)()
    private[this]       val hBands  : InIAux    = InIAux   (this, shape.in5)(math.max(1, _))

    private[this] var magSize     = 0
    private[this] var minFreq     = 0.0
    private[this] var maxFreq     = 0.0
    private[this] var sampleRate  = 0.0
    private[this] var bands       = 0

    private[this] var melBuf    : Array[Double] = _
    private[this] var binIndices: Array[Int   ] = _

    override protected def stopped(): Unit = {
      super.stopped()
      melBuf      = null
      binIndices  = null
    }

    protected           def winBufSize  : Int   = magSize
    override protected  def writeWinSize: Long  = bands

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hLoFreq.hasNext && hHiFreq.hasNext && hSR.hasNext && hBands.hasNext
      if (ok) {
        var updatedBins = false
        val _magSize = hSize.next()
        if (magSize != _magSize) {
          magSize     = _magSize
          updatedBins = true
        }

        val _sampleRate = hSR.next()
        if (sampleRate != _sampleRate) {
          sampleRate  = _sampleRate
          updatedBins = true
        }
        val nyquist   = _sampleRate/2
        val ceil      = (_magSize - 1).toDouble / _magSize * nyquist

        val _minFreq  = hLoFreq.next().clip(0.0, ceil)
        if (minFreq != _minFreq) {
          minFreq     = _minFreq
          updatedBins = true
        }

        val _maxFreq  = hHiFreq.next().clip(_minFreq, ceil)
        if (maxFreq != _maxFreq) {
          maxFreq     = _maxFreq
          updatedBins = true
        }

        val _bands = hBands.next()
        if (bands != _bands) {
          bands     = _bands
          updatedBins = true
        }

        if (updatedBins) {
          melBuf      = new Array[Double](_bands + 2) // we need two samples more internally
          binIndices  = calcBinIndices()
        }
      }
      ok
    }

    override protected def writeFromWindow(n: Int): Unit = {
      // add `+ 1` because we skip that first value
      val offI = writeOff.toInt + 1
      hOut.nextN(melBuf, offI, n)
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

    protected def processWindow(): Unit = {
      val _bands  = bands
      val _melBuf = melBuf
      val _binIdx = binIndices
      val _magBuf = winBuf // magBuf
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
    }
  }
}