/*
 *  SegModPhasor.scala
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

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape2, Outlet}
import de.sciss.fscape.stream.impl.{InOutImpl, NodeImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

import scala.annotation.tailrec

object SegModPhasor {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "SegModPhasor"

  private type Shape = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with InOutImpl[Shape] with Out1LogicImpl[BufD, Shape] {

    private[this] var incr    : Double = _  // single sample delay
    private[this] var phaseOff: Double = 0.0
    private[this] var phase   : Double = 0.0  // internal state; does not include `phaseOff`

    private[this] var bufIn0  : BufD = _
    private[this] var bufIn1  : BufD = _
    protected     var bufOut0 : BufD = _

    private[this] var inOff0    : Int   = 0
    private[this] var inRemain0 : Int   = 0
    private[this] var inOff1    : Int   = 0
    private[this] var inRemain1 : Int   = 0
    private[this] var outOff    : Int   = 0
    private[this] var outRemain : Int   = 0

    private[this] var nextPhase : Boolean = true

    private[this] var inValid0  : Boolean = false
    private[this] var inValid1  : Boolean = false

    private object _InHandlerImpl extends InHandler {
      override def onUpstreamFinish(): Unit = process()

      def onPush(): Unit = process()
    }

    setInHandler(shape.in0, _InHandlerImpl)
    setInHandler(shape.in1, _InHandlerImpl)
    new ProcessOutHandlerImpl(shape.out, this)

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    protected def out0: Outlet[BufD] = shape.out

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    def inValid: Boolean = inValid0 && inValid1

    /** Subclasses can override this */
    override protected def stopped(): Unit = {
      freeInputBuffer0()
      freeInputBuffer1()
      freeOutputBuffers()
      super.stopped()
    }

    private def freeInputBuffer0(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    private def freeInputBuffer1(): Unit =
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }

    private def checkFlush(): Unit =
      if (isClosed(shape.in0) && nextPhase && (outOff == 0 || canWrite)) {
        if (outOff > 0) writeOuts(outOff)
        completeStage()
      }

    @tailrec
    def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (inRemain0 == 0) {
        if (isInAvailable(shape.in0)) {
          freeInputBuffer0()
          bufIn0      = grab(shape.in0)
          tryPull(shape.in0)
          inOff0      = 0
          inRemain0   = bufIn0.size
          inValid0    = true
          stateChange = true
        } else {
          checkFlush()
        }
      }

      if (inRemain1 == 0 && isInAvailable(shape.in1)) {
        freeInputBuffer1()
        bufIn1      = grab(shape.in1)
        tryPull(shape.in1)
        inOff1      = 0
        inRemain1   = bufIn1.size
        inValid1    = true
        stateChange = true
      }

      if (bufOut0 == null) {
        bufOut0     = ctrl.borrowBufD()
        outOff      = 0
        outRemain   = bufOut0.size
        stateChange = true
      }

      if (inValid && outRemain > 0) {
        stateChange |= processChunk()
      }

      if (outRemain == 0 && canWrite) {
        writeOuts(outOff)
        outOff      = 0
        stateChange = true
      }

      if (stateChange) process()
    }

    private def processChunk(): Boolean = {
      var stateChange = false

      var _outRem     = outRemain
      var _incr       = incr
      var _phaseOff   = phaseOff
      var _phase      = phase
      val out         = bufOut0.buf
      var _outOff     = outOff
      var _nextPhase  = nextPhase

      @inline
      def cleanUp(): Boolean = {
        if (_outRem != outRemain) {
          outRemain   = _outRem
          stateChange = true
        }
        outOff    = _outOff
        incr      = _incr
        phaseOff  = _phaseOff
        phase     = _phase
        nextPhase = _nextPhase
        stateChange
      }

      while (_outRem > 0) {
        if (_nextPhase) {
          if (inRemain0 > 0 && (inRemain1 > 0 || isClosed(shape.in1))) {
            stateChange = true
            _incr = bufIn0.buf(inOff0)
            inOff0    += 1
            inRemain0 -= 1
            if (inRemain1 > 0) {
              _phaseOff  = bufIn1.buf(inOff1) % 1.0
              inOff1    += 1
              inRemain1 -= 1
            }
            _nextPhase = false
          } else {
            if (inRemain0 == 0) {
              cleanUp()
              checkFlush()
            }
            return cleanUp()
          }
        }

        val x         = (_phase + _phaseOff) % 1.0
        out(_outOff)  = _phase // x
        _phase        = (_phase + _incr) % 1.0
        val y         =  x      + _incr
        if (y >= 1.0) {
          _nextPhase  = true
        }
        _outOff += 1
        _outRem -= 1
      }

      cleanUp()
    }
  }
}