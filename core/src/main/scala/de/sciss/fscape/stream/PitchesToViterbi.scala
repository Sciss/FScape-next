/*
 *  PitchesToViterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape10, Outlet}
import de.sciss.fscape.stream.impl.{DemandAuxInHandler, DemandInOutImpl, DemandProcessInHandler, DemandWindowedLogic, NodeImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

object PitchesToViterbi {
  def apply(lags: OutD, strengths: OutD, numIn: OutI, peaks: OutD, maxLag: OutI, voicingThresh: OutD, silenceThresh: OutD,
            octaveCost: OutD, octaveJumpCost: OutD, voicedUnvoicedCost: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
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

  private type Shape = FanInShape10[BufD, BufD, BufI, BufD, BufI, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape10(
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

    def createLogic(attr: Attributes): NodeImpl[PitchesToViterbi.Shape] = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with Out1DoubleImpl     [Shape]
      with Out1LogicImpl[BufD, Shape]
      with DemandInOutImpl    [Shape] {

    private[this] var bufIn0 : BufD = _
    private[this] var bufIn1 : BufD = _
    private[this] var bufIn2 : BufI = _
    private[this] var bufIn3 : BufD = _
    private[this] var bufIn4 : BufI = _
    private[this] var bufIn5 : BufD = _
    private[this] var bufIn6 : BufD = _
    private[this] var bufIn7 : BufD = _
    private[this] var bufIn8 : BufD = _
    private[this] var bufIn9 : BufD = _

    protected var bufOut0: BufD = _

    private[this] var _mainCanRead  = false
    private[this] var _auxCanRead   = false
    private[this] var _mainInValid  = false
    private[this] var _auxInValid   = false
    private[this] var _inValid      = false

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
    private[this] var innerMat      : Array[Double] = _

    private[this] var isFirstFrame = true

    protected def out0: Outlet[BufD] = shape.out

    def mainCanRead : Boolean = _mainCanRead
    def auxCanRead  : Boolean = _auxCanRead
    def mainInValid : Boolean = _mainInValid
    def auxInValid  : Boolean = _auxInValid
    def inValid     : Boolean = _inValid

    new DemandProcessInHandler(shape.in0, this)
    new DemandProcessInHandler(shape.in1, this)
    new DemandAuxInHandler    (shape.in2, this)
    new DemandAuxInHandler    (shape.in3, this)
    new DemandAuxInHandler    (shape.in4, this)
    new DemandAuxInHandler    (shape.in5, this)
    new DemandAuxInHandler    (shape.in6, this)
    new DemandAuxInHandler    (shape.in7, this)
    new DemandAuxInHandler    (shape.in8, this)
    new DemandAuxInHandler    (shape.in9, this)
    new ProcessOutHandlerImpl (shape.out, this)

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
      pull(sh.in3)
      pull(sh.in4)
      pull(sh.in5)
      pull(sh.in6)
      pull(sh.in7)
      pull(sh.in8)
      pull(sh.in9)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      lagsPrev      = null
      lagsCurr      = null
      strengthsPrev = null
      strengthsCurr = null
      innerMat      = null
      freeInputBuffers()
      freeOutputBuffers()
    }

    protected def readMainIns(): Int = {
      freeMainInBuffers()
      val sh        = shape
      bufIn0        = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      bufIn1        = grab(sh.in1)
      bufIn1.assertAllocated()
      tryPull(sh.in1)

      if (!_mainInValid) {
        _mainInValid= true
        _inValid    = _auxInValid
      }

      _mainCanRead = false
      math.min(bufIn0.size, bufIn1.size)
    }

    private def freeInputBuffers(): Unit = {
      freeMainInBuffers()
      freeAuxInBuffers()
    }

    private def freeAuxInBuffers(): Unit = {
      if (bufIn2 != null) {
        bufIn2.release()
        bufIn2 = null
      }
      if (bufIn3 != null) {
        bufIn3.release()
        bufIn3 = null
      }
      if (bufIn4 != null) {
        bufIn4.release()
        bufIn4 = null
      }
      if (bufIn5 != null) {
        bufIn5.release()
        bufIn5 = null
      }
      if (bufIn6 != null) {
        bufIn6.release()
        bufIn6 = null
      }
      if (bufIn7 != null) {
        bufIn7.release()
        bufIn7 = null
      }
      if (bufIn8 != null) {
        bufIn8.release()
        bufIn8 = null
      }
      if (bufIn9 != null) {
        bufIn9.release()
        bufIn9 = null
      }
    }

    private def freeMainInBuffers(): Unit = {
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    protected def readAuxIns(): Int = {
      freeAuxInBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in2)) {
        bufIn2  = grab(sh.in2)
        sz      = math.max(sz, bufIn2.size)
        tryPull(sh.in2)
      }
      if (isAvailable(sh.in3)) {
        bufIn3  = grab(sh.in3)
        sz      = math.max(sz, bufIn3.size)
        tryPull(sh.in3)
      }
      if (isAvailable(sh.in4)) {
        bufIn4  = grab(sh.in4)
        sz      = math.max(sz, bufIn4.size)
        tryPull(sh.in4)
      }
      if (isAvailable(sh.in5)) {
        bufIn5  = grab(sh.in5)
        sz      = math.max(sz, bufIn5.size)
        tryPull(sh.in5)
      }
      if (isAvailable(sh.in6)) {
        bufIn6  = grab(sh.in6)
        sz      = math.max(sz, bufIn6.size)
        tryPull(sh.in6)
      }
      if (isAvailable(sh.in7)) {
        bufIn7  = grab(sh.in7)
        sz      = math.max(sz, bufIn7.size)
        tryPull(sh.in7)
      }
      if (isAvailable(sh.in8)) {
        bufIn8  = grab(sh.in8)
        sz      = math.max(sz, bufIn8.size)
        tryPull(sh.in8)
      }
      if (isAvailable(sh.in9)) {
        bufIn9  = grab(sh.in9)
        sz      = math.max(sz, bufIn9.size)
        tryPull(sh.in9)
      }

      if (!_auxInValid) {
        _auxInValid = true
        _inValid    = _mainInValid
      }

      _auxCanRead = false
      sz
    }

    def updateAuxCanRead(): Unit = {
      val sh = shape
      _auxCanRead =
        ((isClosed(sh.in2) && _auxInValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _auxInValid) || isAvailable(sh.in3)) &&
        ((isClosed(sh.in4) && _auxInValid) || isAvailable(sh.in4)) &&
        ((isClosed(sh.in5) && _auxInValid) || isAvailable(sh.in5)) &&
        ((isClosed(sh.in6) && _auxInValid) || isAvailable(sh.in6)) &&
        ((isClosed(sh.in7) && _auxInValid) || isAvailable(sh.in7)) &&
        ((isClosed(sh.in8) && _auxInValid) || isAvailable(sh.in8)) &&
        ((isClosed(sh.in9) && _auxInValid) || isAvailable(sh.in9))
    }

    def updateMainCanRead(): Unit = {
      val sh = shape
      _mainCanRead = isAvailable(sh.in0) && isAvailable(sh.in1)
    }

    protected def inputsEnded: Boolean = {
      val sh = shape
      mainInRemain == 0 &&
        ((isClosed(sh.in0) && !isAvailable(sh.in0)) || (isClosed(sh.in1) && !isAvailable(sh.in1)))
    }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    protected def startNextWindow(): Long = {
      // println(s"- window: $auxInOff, ${if (bufIn3 == null) "null" else bufIn3.size}")

      // n: 2, peaks: 3, maxLag: 4, voicingThresh: 5, silenceThresh: 6, octaveCost: 7, octaveJumpCost: 8, voicedUnvoicedCost: 9
      val inOff = auxInOff
      if (bufIn2 != null && inOff < bufIn2.size) {
        val oldN = numStatesIn
        val _numStatesIn = math.max(0, bufIn2.buf(inOff))
        if (_numStatesIn != oldN) {
          val _numStatesOut = _numStatesIn + 1
          numStatesIn   = _numStatesIn
          lagsPrev      = new Array(_numStatesOut)
          lagsCurr      = new Array(_numStatesOut)
          strengthsPrev = new Array(_numStatesOut)
          strengthsCurr = new Array(_numStatesOut)
          statesSq      = _numStatesOut * _numStatesOut
          innerMat      = new Array(statesSq)
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        peak = math.max(0.0, bufIn3.buf(inOff))
//        println(s"peak: $peak")
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        maxLag = math.max(1, bufIn4.buf(inOff))
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        voicingThresh = math.max(0.0, bufIn5.buf(inOff))
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        silenceThresh = math.max(0.0, bufIn6.buf(inOff))
      }
      if (bufIn7 != null && inOff < bufIn7.size) {
        octaveCost = bufIn7.buf(inOff) / Util.log2
      }
      if (bufIn8 != null && inOff < bufIn8.size) {
        octaveJumpCost = bufIn8.buf(inOff) / Util.log2
      }
      if (bufIn9 != null && inOff < bufIn9.size) {
        voicedUnvoicedCost = bufIn9.buf(inOff)
      }

      numStatesIn
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && {
      val sh = shape
      import sh._
      isClosed(in2) && isClosed(in3) && isClosed(in4) && isClosed(in5) &&
      isClosed(in6) && isClosed(in7) && isClosed(in8) && isClosed(in9)
    })

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      val off = writeToWinOff.toInt
      Util.copy(bufIn0.buf, mainInOff, lagsCurr     , off, chunk)
      Util.copy(bufIn1.buf, mainInOff, strengthsCurr, off, chunk)
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val inOff = readFromWinOff.toInt
      Util.copy(innerMat, inOff, bufOut0.buf, outOff, chunk)
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      val off = writeToWinOff.toInt
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
        _voicingThresh + math.max(0.0, 2.0 - peak * (1.0 + _voicingThresh) / _silenceThresh)
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
          val strengthC = strength - _octaveCost * math.log(_maxLag / lag)
//          val strengthC1 = strength + _octaveCost * math.log(147 / lag)
          _strengths(i) = strengthC
          i += 1
        }
      }

      val _mat = innerMat

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
              _octaveJumpCost * math.abs(math.log(lagCurr / lagPrev))
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

      statesSq
    }
  }
}
