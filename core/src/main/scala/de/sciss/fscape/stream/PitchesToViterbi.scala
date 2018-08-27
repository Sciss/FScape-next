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

import akka.stream.{Attributes, FanInShape8, Outlet}
import de.sciss.fscape.stream.impl.{DemandAuxInHandler, DemandInOutImpl, DemandProcessInHandler, DemandWindowedLogic, NodeImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

object PitchesToViterbi {
  def apply(lags: OutD, strengths: OutD, n: OutI, voicingThresh: OutD, silenceThresh: OutD, octaveCost: OutD,
            octaveJumpCost: OutD, voicedUnvoicedCost: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(lags              , stage.in0)
    b.connect(strengths         , stage.in1)
    b.connect(n                 , stage.in2)
    b.connect(voicingThresh     , stage.in3)
    b.connect(silenceThresh     , stage.in4)
    b.connect(octaveCost        , stage.in5)
    b.connect(octaveJumpCost    , stage.in6)
    b.connect(voicedUnvoicedCost, stage.in7)
    stage.out
  }

  private final val name = "PitchesToViterbi"

  private type Shape = FanInShape8[BufD, BufD, BufI, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
      in0 = InD (s"$name.lags"              ),
      in1 = InD (s"$name.strengths"         ),
      in2 = InI (s"$name.n"                 ),
      in3 = InD (s"$name.voicingThresh"     ),
      in4 = InD (s"$name.silenceThresh"     ),
      in5 = InD (s"$name.octaveCost"        ),
      in6 = InD (s"$name.octaveJumpCost"    ),
      in7 = InD (s"$name.voicedUnvoicedCost"),
      out = OutD(s"$name.out"               )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with Out1DoubleImpl[Shape] with Out1LogicImpl[BufD, Shape] 
      with DemandInOutImpl[Shape] {

    private[this] var bufIn0 : BufD = _
    private[this] var bufIn1 : BufD = _
    private[this] var bufIn2 : BufI = _
    private[this] var bufIn3 : BufD = _
    private[this] var bufIn4 : BufD = _
    private[this] var bufIn5 : BufD = _
    private[this] var bufIn6 : BufD = _
    private[this] var bufIn7 : BufD = _

    protected var bufOut0: BufD = _

    private[this] var _mainCanRead  = false
    private[this] var _auxCanRead   = false
    private[this] var _mainInValid  = false
    private[this] var _auxInValid   = false
    private[this] var _inValid      = false

    private[this] var numStates         : Int = -1
    private[this] var statesSq          : Int = _
    private[this] var voicingThresh     : Double = _
    private[this] var silenceThresh     : Double = _
    private[this] var octaveCost        : Double = _
    private[this] var octaveJumpCost    : Double = _
    private[this] var voicedUnvoicedCost: Double = _

    private[this] var deltaPrev : Array[Double] = _
    private[this] var deltaCurr : Array[Double] = _
    private[this] var innerMat  : Array[Double] = _

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
    new ProcessOutHandlerImpl (shape.out, this)

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      deltaPrev = null
      deltaCurr = null
      innerMat  = null
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

      if (isAvailable(sh.in2)) {
        bufIn2  = grab(sh.in2)
        sz      = math.max(sz, bufIn2.size)
        tryPull(sh.in2)
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
        ((isClosed(sh.in7) && _auxInValid) || isAvailable(sh.in7))
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
      // n: 2, voicingThresh: 3, silenceThresh: 4, octaveCost: 5, octaveJumpCost: 6, voicedUnvoicedCost: 7
      val inOff = auxInOff
      if (bufIn2 != null && inOff < bufIn2.size) {
        val oldN = numStates
        numStates = math.max(1, bufIn2.buf(inOff))
        if (numStates != oldN) {
          deltaPrev = new Array(numStates)
          deltaCurr = new Array(numStates)
          statesSq  = numStates * numStates
          innerMat  = new Array(statesSq)
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        voicingThresh = math.max(0.0, bufIn3.buf(inOff))
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        silenceThresh = math.max(0.0, bufIn4.buf(inOff))
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        octaveCost = bufIn5.buf(inOff)
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        octaveJumpCost = bufIn6.buf(inOff)
      }
      if (bufIn7 != null && inOff < bufIn7.size) {
        voicedUnvoicedCost = bufIn7.buf(inOff)
      }

      numStates
    }

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      // to deltaCurr
      ???
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      // from innerMat
      ???
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      ???

      statesSq
    }
  }
}
