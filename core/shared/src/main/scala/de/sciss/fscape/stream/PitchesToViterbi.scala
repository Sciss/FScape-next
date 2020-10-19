/*
 *  PitchesToViterbi.scala
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

import akka.stream.{Attributes, FanInShape10}
import de.sciss.fscape.Util.log2
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedInA1A2OutB
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.math.{abs, log, max}

object PitchesToViterbi {
  def apply(lags: OutD, strengths: OutD, numIn: OutI, peaks: OutD, maxLag: OutI, voicingThresh: OutD,
            silenceThresh: OutD, octaveCost: OutD, octaveJumpCost: OutD, voicedUnvoicedCost: OutD)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(lags              , stage.in0)
    b.connect(strengths         , stage.in1)
    b.connect(numIn             , stage.in2)
    b.connect(peaks             , stage.in3)
    b.connect(maxLag            , stage.in4)
    b.connect(voicingThresh     , stage.in5)
    b.connect(silenceThresh     , stage.in6)
    b.connect(octaveCost        , stage.in7)
    b.connect(octaveJumpCost    , stage.in8)
    b.connect(voicedUnvoicedCost, stage.in9)
    stage.out
  }

  private final val name = "PitchesToViterbi"

  private type Shp = FanInShape10[BufD, BufD, BufI, BufD, BufI, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape10(
      in0 = InD (s"$name.lags"              ),
      in1 = InD (s"$name.strengths"         ),
      in2 = InI (s"$name.numIn"             ),
      in3 = InD (s"$name.peaks"             ),
      in4 = InI (s"$name.maxLag"            ),
      in5 = InD (s"$name.voicingThresh"     ),
      in6 = InD (s"$name.silenceThresh"     ),
      in7 = InD (s"$name.octaveCost"        ),
      in8 = InD (s"$name.octaveJumpCost"    ),
      in9 = InD (s"$name.voicedUnvoicedCost"),
      out = OutD(s"$name.out"               )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape)
      with WindowedInA1A2OutB[Double, BufD, Double, BufD, Double, BufD, Double] {

    protected     val hIn1      : InDMain   = InDMain (this, shape.in0)
    protected     val hIn2      : InDMain   = InDMain (this, shape.in1)
    protected     val hOut      : OutDMain  = OutDMain(this, shape.out)
    private[this] val hNumIn    : InIAux    = InIAux  (this, shape.in2)(max(0 , _))
    private[this] val hPeaks    : InDAux    = InDAux  (this, shape.in3)(max(0.0, _))
    private[this] val hMaxLag   : InIAux    = InIAux  (this, shape.in4)(max(1 , _))
    private[this] val hVcThresh : InDAux    = InDAux  (this, shape.in5)(max(0.0, _))
    private[this] val hSilThresh: InDAux    = InDAux  (this, shape.in6)(max(0.0, _))
    private[this] val hOctCost  : InDAux    = InDAux  (this, shape.in7)(_ / log2)
    private[this] val hOctJump  : InDAux    = InDAux  (this, shape.in8)(_ / log2)
    private[this] val hVcCost   : InDAux    = InDAux  (this, shape.in9)()

    private[this] var numStatesIn       : Int     = -1
    private[this] var statesSq          : Int     = _
    private[this] var peak              : Double  = _
    private[this] var maxLag            : Int     = _
    private[this] var voicingThresh     : Double  = _
    private[this] var silenceThresh     : Double  = _
    private[this] var octaveCost        : Double  = _
    private[this] var octaveJumpCost    : Double  = _
    private[this] var voicedUnvoicedCost: Double  = _

    private[this] var lagsPrev      : Array[Double] = _
    private[this] var lagsCurr      : Array[Double] = _
    private[this] var strengthsPrev : Array[Double] = _
    private[this] var strengthsCurr : Array[Double] = _

    private[this] var isFirstFrame = true

    protected def a1Tpe : StreamType[Double, BufD] = StreamType.double
    protected def a2Tpe : StreamType[Double, BufD] = StreamType.double
    protected def bTpe  : StreamType[Double, BufD] = StreamType.double

    protected def newWindowBuffer(n: Int): Array[Double] = new Array(n)

    override protected def stopped(): Unit = {
      super.stopped()
      lagsPrev      = null
      lagsCurr      = null
      strengthsPrev = null
      strengthsCurr = null
    }

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hNumIn    .hasNext &&
        hPeaks    .hasNext &&
        hMaxLag   .hasNext &&
        hVcThresh .hasNext &&
        hSilThresh.hasNext &&
        hOctCost  .hasNext &&
        hOctJump  .hasNext &&
        hVcCost   .hasNext

      if (ok) {
        val oldN = numStatesIn
        val _numStatesIn = hNumIn.next()
        if (_numStatesIn != oldN) {
          val _numStatesOut = _numStatesIn + 1
          numStatesIn   = _numStatesIn
          lagsPrev      = new Array(_numStatesOut)
          lagsCurr      = new Array(_numStatesOut)
          strengthsPrev = new Array(_numStatesOut)
          strengthsCurr = new Array(_numStatesOut)
          statesSq      = _numStatesOut * _numStatesOut
        }

        peak                = hPeaks    .next()
        maxLag              = hMaxLag   .next()
        voicingThresh       = hVcThresh .next()
        silenceThresh       = hSilThresh.next()
        octaveCost          = hOctCost  .next()
        octaveJumpCost      = hOctJump  .next()
        voicedUnvoicedCost  = hVcCost   .next()
      }
      ok
    }

    protected def winBufSize: Int = statesSq

    override protected def readWinSize: Long = numStatesIn

    protected def readIntoWindow(chunk: Int): Unit = {
      val off = readOff.toInt
      hIn1.nextN(lagsCurr     , off, chunk)
      hIn2.nextN(strengthsCurr, off, chunk)
    }

    protected def writeFromWindow(chunk: Int): Unit = {
      val off = writeOff.toInt
      hOut.nextN(winBuf, off, chunk)
    }

    protected def clearWindowTail(): Unit = {
      val off   = readOff.toInt
      val chunk = numStatesIn - off
      Util.clear(lagsCurr     , off, chunk)
      Util.clear(strengthsCurr, off, chunk)
    }

    protected def processWindow(): Unit = {
      val off = readOff.toInt
      val _numStatesIn  = numStatesIn
      val _numStatesOut = _numStatesIn + 1
      val _lags         = lagsCurr
      val _strengths    = strengthsCurr
      if (off < _numStatesIn) {
        Util.clear(_lags     , off, _numStatesIn - off)
        Util.clear(_strengths, off, _numStatesIn - off)
      }

      val _silenceThresh  = silenceThresh
      val _voicingThresh  = voicingThresh
      val _noSil          = _silenceThresh == 0.0
      val _maxLag         = maxLag
      val _octaveCost     = octaveCost
      val _unvoicedStrength = if (_noSil) _voicingThresh else {
        _voicingThresh + max(0.0, 2.0 - peak * (1.0 + _voicingThresh) / _silenceThresh)
      }

      // first update the strengths to include octave costs etc.
      var i = 0
      while (i < _numStatesOut) {
        val lag = if (i < _numStatesIn) _lags(i) else 0.0
        if (lag == 0.0) { // unvoiced
          _strengths(i) = _unvoicedStrength
          i += 1
          while (i < _numStatesOut) {
            _strengths(i) = _unvoicedStrength  // 0.0 // Double.NegativeInfinity
            i += 1
          }

        } else {
          val strength  = _strengths(i)
          // not sure what's right here
          // cf. https://github.com/praat/praat/issues/662
          // Praat has
          //   -OctaveCost * log2 (ceiling / candidate_frequency)
          // But paper has
          //   -OctaveCost * log2 (MinimumPitch * lag)
          //
          // So in the paper: if we have the minimum-frequency, the cost is zero,
          // if we have twice the minimum frequency (half the lag), we would _add_
          // octave-cost to the strength.
          // Whereas in Praat, if we have the maximum-frequency, the cost is zero,
          // if we have half the maximum frequency, we would _subtract_ octave cost
          // from the strength. The direction is the same (higher frequencies are
          // preferred), but the total cost amount is different.
          val strengthC = strength - _octaveCost * log(_maxLag / lag)
//          val strengthC1 = strength + _octaveCost * math.log(147 / lag)
          _strengths(i) = strengthC
          i += 1
        }
      }

      val _mat = winBuf

      if (isFirstFrame) {
        isFirstFrame = false
        // we fill each row of the output matrix with
        // one value of the initial delta vector
        i = 0
        var k = 0
        while (i < _numStatesOut) {
          var j = 0
          val v = _strengths(i)
          while (j < _numStatesOut) {
            _mat(k) = v
            j += 1
            k += 1
          }
          i += 1
        }

      } else {
        val _lagsPrev           = lagsPrev
        val _voicedUnvoicedCost = voicedUnvoicedCost
        val _octaveJumpCost     = octaveJumpCost

        i = 0
        var k = 0
        while (i < _numStatesOut) {
          var j = 0
          val lagCurr       = _lags     (i)
          val strengthCurr  = _strengths(i)
          val currVoiceless = lagCurr == 0
          while (j < _numStatesOut) {
            val lagPrev       = _lagsPrev(j)
            val prevVoiceless = lagPrev == 0
            val cost = if (currVoiceless ^ prevVoiceless) {
              _voicedUnvoicedCost
            } else if (currVoiceless /* & prevVoiceless */) {
              0.0
            } else {
              _octaveJumpCost * abs(log(lagCurr / lagPrev))
            }
            _mat(k) = strengthCurr - cost
            j += 1
            k += 1
          }
          i += 1
        }
      }

      // swap buffers
      lagsCurr      = lagsPrev
      lagsPrev      = _lags
      strengthsCurr = strengthsPrev
      strengthsPrev = _strengths
    }
  }
}
