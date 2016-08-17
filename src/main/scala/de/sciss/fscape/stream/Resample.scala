/*
 *  Resample.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape6, Inlet}
import de.sciss.fscape.stream.impl.{StageImpl, StageLogicImpl}

import scala.annotation.tailrec

object Resample {
  def apply(in: OutD, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in           , stage.in0)
    b.connect(factor       , stage.in1)
    b.connect(minFactor    , stage.in2)
    b.connect(rollOff      , stage.in3)
    b.connect(kaiserBeta   , stage.in4)
    b.connect(zeroCrossings, stage.in5)
    stage.out
  }

  private final val name = "Resample"

  private type Shape = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"           ),
      in1 = InD (s"$name.factor"       ),
      in2 = InD (s"$name.minFactor"    ),
      in3 = InD (s"$name.rollOff"      ),
      in4 = InD (s"$name.kaiserBeta"   ),
      in5 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private val fltSmpPerCrossing = 4096

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape) {

    private[this] var factor        = -1.0
    private[this] var minFactor     = -1.0
    private[this] var rollOff       = -1.0
    private[this] var kaiserBeta    = -1.0
    private[this] var zeroCrossings = -1

    private[this] var bufIn           : BufD = _
    private[this] var bufFactor       : BufD = _
    private[this] var bufMinFactor    : BufD = _
    private[this] var bufRollOff      : BufD = _
    private[this] var bufKaiserBeta   : BufD = _
    private[this] var bufZeroCrossings: BufI = _
    private[this] var bufOut          : BufD = _

    private[this] var _inMainValid  = false
    private[this] var _inAuxValid   = false
    private[this] var _canReadMain  = false
    private[this] var _canReadAux   = false
    private[this] var _canWrite     = false

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
      pull(sh.in3)
      pull(sh.in4)
      pull(sh.in5)
    }

    override def postStop(): Unit = {
      freeMainInputBuffers()
      freeAuxInputBuffers()
      freeOutputBuffers()
    }

    private def freeMainInputBuffers(): Unit =
      if (bufIn != null) {
        bufIn.release()
        bufIn = null
      }

    private def freeAuxInputBuffers(): Unit = {
      if (bufFactor != null) {
        bufFactor.release()
        bufFactor = null
      }
      if (bufMinFactor != null) {
        bufMinFactor.release()
        bufMinFactor = null
      }
      if (bufRollOff != null) {
        bufRollOff.release()
        bufRollOff = null
      }
      if (bufKaiserBeta != null) {
        bufKaiserBeta.release()
        bufKaiserBeta = null
      }
      if (bufZeroCrossings != null) {
        bufZeroCrossings.release()
        bufZeroCrossings = null
      }
    }

    private def freeOutputBuffers(): Unit =
      if (bufOut != null) {
        bufOut.release()
        bufOut = null
      }

    private def readMainIns(): Int = {
      freeMainInputBuffers()
      bufIn = grab(shape.in0)
      tryPull(shape.in0)
      bufIn.size
    }

    private def readAuxIns(): Int = {
      freeAuxInputBuffers()
      val sh  = shape
      var res = 0

      if (isAvailable(sh.in1)) {
        bufFactor = grab(sh.in1)
        tryPull(sh.in1)
        res = bufFactor.size
      }

      if (isAvailable(sh.in2)) {
        bufMinFactor = grab(sh.in2)
        tryPull(sh.in2)
        res = if (res == 0) bufMinFactor.size else math.min(res, bufMinFactor.size)
      }

      if (isAvailable(sh.in3)) {
        bufRollOff = grab(sh.in3)
        tryPull(sh.in3)
        res = if (res == 0) bufRollOff.size else math.min(res, bufRollOff.size)
      }

      if (isAvailable(sh.in4)) {
        bufKaiserBeta = grab(sh.in4)
        tryPull(sh.in4)
        res = if (res == 0) bufKaiserBeta.size else math.min(res, bufKaiserBeta.size)
      }

      if (isAvailable(sh.in5)) {
        bufZeroCrossings = grab(sh.in5)
        tryPull(sh.in5)
        res = if (res == 0) bufZeroCrossings.size else math.min(res, bufZeroCrossings.size)
      }

//      if (!_inAuxValid) {
//        // calculate buffer only once
//        val fltLen = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
        _inAuxValid = true
//      }

      _canReadAux = false
      res
    }

    private class AuxInHandler[A](in: Inlet[A])
      extends InHandler {

      def onPush(): Unit = {
        logStream(s"onPush($in)")
        testRead()
      }

      private[this] def testRead(): Unit = {
        updateCanReadAux()
        if (_canReadAux) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (_inAuxValid || isAvailable(in)) {
          testRead()
        } else {
          println(s"Invalid aux $in")
          completeStage()
        }
      }

      setHandler(in, this)
    }

    new AuxInHandler(shape.in1)
    new AuxInHandler(shape.in2)
    new AuxInHandler(shape.in3)
    new AuxInHandler(shape.in4)
    new AuxInHandler(shape.in5)

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush(${shape.in0})")
        _canReadMain = true
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish(${shape.in0})")
        if (_inMainValid) process() // may lead to `flushOut`
        else {
          if (!isAvailable(shape.in0)) {
            println(s"Invalid process ${shape.in0}")
            completeStage()
          }
        }
      }
    })

    setHandler(shape.out, new OutHandler {
      def onPull(): Unit = {
        logStream(s"onPull(${shape.out})")
        _canWrite = true
        process()
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish(${shape.out})")
        super.onDownstreamFinish()
      }
    })

    @tailrec
    private def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      ???

//      if (flushOut && outSent) {
//        logStream(s"completeStage() $this")
//        completeStage()
//      }
//      else
      if (stateChange) process()
    }

    private def updateCanReadAux(): Unit = {
      val sh = shape
      _canReadAux =
        ((isClosed(sh.in1) && _inAuxValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _inAuxValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _inAuxValid) || isAvailable(sh.in3)) &&
        ((isClosed(sh.in4) && _inAuxValid) || isAvailable(sh.in4)) &&
        ((isClosed(sh.in5) && _inAuxValid) || isAvailable(sh.in5))
    }

    //      val inOffI = inOff
//
//      if (bufIn3 != null && inOffI < bufIn3.size) {
//        val newRollOff = math.max(0.0, math.min(1.0, bufIn3.buf(inOffI)))
//        if (rollOff != newRollOff) {
//          rollOff   = newRollOff
//          newTable  = true
//        }
//      }
//
//      if (bufIn4 != null && inOffI < bufIn4.size) {
//        val newKaiserBeta = math.max(0.0, bufIn4.buf(inOffI))
//        if (kaiserBeta != newKaiserBeta) {
//          kaiserBeta  = newKaiserBeta
//          newTable    = true
//        }
//      }
//
//      if (bufIn5 != null && inOffI < bufIn5.size) {
//        val newZeroCrossings = math.max(1, bufIn5.buf(inOffI))
//        if (zeroCrossings != newZeroCrossings) {
//          zeroCrossings = newZeroCrossings
//          newTable      = true
//        }
//      }
  }
}