/*
 *  ScanImage.scala
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

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape10}
import de.sciss.fscape.stream.impl.{NodeImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, ScanImageImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{max, min}

object ScanImage {
  def apply(in: OutD, width: OutI, height: OutI, x: OutD, y: OutD, next: OutI, wrap: OutI,
            rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in            , stage.in0)
    b.connect(width         , stage.in1)
    b.connect(height        , stage.in2)
    b.connect(x             , stage.in3)
    b.connect(y             , stage.in4)
    b.connect(next          , stage.in5)
    b.connect(wrap          , stage.in6)
    b.connect(rollOff       , stage.in7)
    b.connect(kaiserBeta    , stage.in8)
    b.connect(zeroCrossings , stage.in9)
    stage.out
  }

  private final val name = "ScanImage"

  private type Shape = FanInShape10[BufD, BufI, BufI, BufD, BufD, BufI, BufI, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape10(
      in0 = InD (s"$name.in"),
      in1 = InI (s"$name.width"),
      in2 = InI (s"$name.height"),
      in3 = InD (s"$name.x"),
      in4 = InD (s"$name.y"),
      in5 = InI (s"$name.next"),
      in6 = InI (s"$name.wrap"),
      in7 = InD (s"$name.rollOff"),
      in8 = InD (s"$name.kaiserBeta"),
      in9 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with Out1LogicImpl[BufD, Shape]
      with Out1DoubleImpl[Shape]
      with ScanImageImpl {

    /*

      All window defining parameters (`width`, `height`)
      are polled once per matrix. All matrix and filter parameters are polled one per
      output pixel.

     */

    protected def in0 : InD   = shape.in0
    protected def out0: OutD  = shape.out

    private[this] var _mainInRemain = 0
    private[this] var mainInOff     = 0

    private[this] var aux1InRemain  = 0
    private[this] var aux1InOff     = 0

    private[this] var aux2InRemain  = 0
    private[this] var aux2InOff     = 0

    private[this] var outOff        = 0  // regarding `bufOut`
    private[this] var outRemain     = 0

    private[this] var outSent       = true

    private[this] var writeToWinOff     = 0
    private[this] var writeToWinRemain  = 0
    private[this] var isNextWindow      = true
    private[this] var wasNextWindow     = false

    protected def mainInRemain: Int = _mainInRemain

    protected     var bufIn0          : BufD = _   // in

    protected     var bufWidthIn      : BufI = _
    protected     var bufHeightIn     : BufI = _

    private[this] var bufX            : BufD = _
    private[this] var bufY            : BufD = _
    private[this] var bufNext         : BufI = _
    protected     var bufWrap         : BufI = _
    protected     var bufRollOff      : BufD = _
    protected     var bufKaiserBeta   : BufD = _
    protected     var bufZeroCrossings: BufI = _

    protected     var bufOut0: BufD = _

    private[this] var _inValid = false

    private[this] var x = 0.0
    private[this] var y = 0.0

    def inValid: Boolean = _inValid

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
    }

    private def freeInputBuffers(): Unit = {
      freeImageBuffer()
      freeMainInBuffers()
      freeAux1InBuffers()
      freeAux2InBuffers()
    }

    // -------- main input ---------

    private[this] var _mainInValid  = false
    private[this] var _mainCanRead  = false

    private def updateMainCanRead(): Unit =
      _mainCanRead = isAvailable(in0)

    private def freeMainInBuffers(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    private def readMainIns(): Int = {
      freeMainInBuffers()
      val sh        = shape
      bufIn0        = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      if (!_mainInValid) {
        _mainInValid= true
        _inValid    = _aux1InValid && _aux2InValid
      }

      _mainCanRead = false
      bufIn0.size
    }

    // `in` is regular hot input
    // XXX TODO --- this is not true;
    // must change onUpstreamFinish;
    // this is rather the case for x/y
    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush(${shape.in0})")
        updateMainCanRead()
        if (_mainCanRead) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish(${shape.in0})")
        if (inValid) {
          process()
        } else if (!isAvailable(shape.in0)) {
          logStream(s"Invalid process ${shape.in0}")
          completeStage()
        }
      }
    })

    // -------- aux (per window) input ---------

    private[this] var _aux1InValid  = false
    private[this] var _aux1CanRead  = false

    private def updateAux1CanRead(): Unit = {
      val sh = shape
      _aux1CanRead =
        ((isClosed(sh.in1) && _aux1InValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _aux1InValid) || isAvailable(sh.in2))
    }

    private def freeAux1InBuffers(): Unit = {
      if (bufWidthIn != null) {
        bufWidthIn.release()
        bufWidthIn = null
      }
      if (bufHeightIn != null) {
        bufHeightIn.release()
        bufHeightIn = null
      }
    }

    private def readAux1Ins(): Int = {
      freeAux1InBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in1)) {
        bufWidthIn  = grab(sh.in1)
        sz          = bufWidthIn.size
        tryPull(sh.in1)
      }
      if (isAvailable(sh.in2)) {
        bufHeightIn = grab(sh.in2)
        sz          = max(sz, bufHeightIn.size)
        tryPull(sh.in2)
      }

      if (!_aux1InValid) {
        _aux1InValid  = true
        _inValid      = _mainInValid && _aux2InValid
      }

      _aux1CanRead = false
      sz
    }

    // `width`, `height`, are per-window auxiliary inputs
    (1 to 2).foreach { inIdx =>
      val in = shape.inlets(inIdx)
      setHandler(in, new InHandler {
        def onPush(): Unit = {
          logStream(s"onPush($in)")
          updateAux1CanRead()
          if (_aux1CanRead) process()
        }

        override def onUpstreamFinish(): Unit = {
          logStream(s"onUpstreamFinish($in)")
          if (_aux1InValid || isAvailable(in)) {
            updateAux1CanRead()
            // updateAux1Ended()
            if (_aux1CanRead) process()
          } else {
            logStream(s"Invalid aux $in")
            completeStage()
          }
        }
      })
    }

    // -------- aux (ongoing output) input ---------

    private[this] var _aux2InValid  = false
    private[this] var _aux2CanRead  = false
    private[this] var _xyEnded      = false

    private def updateAux2CanRead(): Unit = {
      val sh = shape
      _aux2CanRead =
        isAvailable(sh.in3) &&
        isAvailable(sh.in4) &&
        ((isClosed(sh.in5) && _aux2InValid) || isAvailable(sh.in5)) &&
        ((isClosed(sh.in6) && _aux2InValid) || isAvailable(sh.in6)) &&
        ((isClosed(sh.in7) && _aux2InValid) || isAvailable(sh.in7)) &&
        ((isClosed(sh.in8) && _aux2InValid) || isAvailable(sh.in8)) &&
        ((isClosed(sh.in9) && _aux2InValid) || isAvailable(sh.in9))

      // println(s"updateAux2CanRead(): ${_aux2CanRead}")
    }

    private def updateXYEnded(): Unit = {
      val sh = shape
      _xyEnded = isClosed(sh.in3) && isClosed(sh.in4)
    }

    private def freeAux2InBuffers(): Unit = {
      if (bufX != null) {
        bufX.release()
        bufX = null
      }
      if (bufY != null) {
        bufY.release()
        bufY = null
      }
      if (bufNext != null) {
        bufNext.release()
        bufNext = null
      }
      if (bufWrap != null) {
        bufWrap.release()
        bufWrap = null
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

    private def readAux2Ins(): Int = {
      // println(s"readAux2Ins(); valid? ${_aux2InValid}; canRead? ${_aux2CanRead}")
      freeAux2InBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in3)) {
        // println("...hasX")
        bufX    = grab(sh.in3)
        sz      = bufX.size
        tryPull(sh.in3)
      }
      if (isAvailable(sh.in4)) {
        // println("...hasY")
        bufY    = grab(sh.in4)
        sz      = max(sz, bufY.size)
        tryPull(sh.in4)
      }
      if (isAvailable(sh.in5)) {
        // println("...hasNext")
        bufNext = grab(sh.in5)
        sz      = max(sz, bufNext.size)
        tryPull(sh.in5)
      }
      if (isAvailable(sh.in6)) {
        // println("...hasWrap")
        bufWrap = grab(sh.in6)
        sz      = max(sz, bufWrap.size)
        tryPull(sh.in6)
      }
      if (isAvailable(sh.in7)) {
        // println("...hasRollOff")
        bufRollOff  = grab(sh.in7)
        sz          = max(sz, bufRollOff.size)
        tryPull(sh.in7)
      }
      if (isAvailable(sh.in8)) {
        // println("...hasKaiserBeta")
        bufKaiserBeta = grab(sh.in8)
        sz            = max(sz, bufKaiserBeta.size)
        tryPull(sh.in8)
      }
      if (isAvailable(sh.in9)) {
        // println("...hasZeroCrossings")
        bufZeroCrossings  = grab(sh.in9)
        sz                = max(sz, bufZeroCrossings.size)
        tryPull(sh.in9)
      }

      if (!_aux2InValid) {
        _aux2InValid  = true
        _inValid      = _mainInValid && _aux1InValid
        // println(s"...`_aux2InValid` became true; `inValid` = ${_inValid}")
      }

      _aux2CanRead = false
      sz
    }

    // the matrix and filter inputs are output driven
    (3 to 9).foreach { inIdx =>
      val in = shape.inlets(inIdx)
      setHandler(in, new InHandler {
        def onPush(): Unit = {
          logStream(s"onPush($in)")
          updateAux2CanRead()
          if (_aux2CanRead) process()
        }

        override def onUpstreamFinish(): Unit = {
          logStream(s"onUpstreamFinish($in)")
          if (_aux2InValid || isAvailable(in)) {
            updateAux2CanRead()
            updateXYEnded()
            if (_aux2CanRead || _xyEnded) process()  // may lead to `flushOut`
          } else {
            logStream(s"Invalid aux $in")
            completeStage()
          }
        }
      })
    }

    // -------- output ---------

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    // -------- output ---------
    new ProcessOutHandlerImpl(shape.out, this)

    // -------- process ---------

    @inline
    private[this] def mainShouldRead = _mainInRemain == 0 && _mainCanRead

    @inline
    private[this] def aux1ShouldRead = aux1InRemain == 0 && _aux1CanRead

    @inline
    private[this] def aux2ShouldRead = aux2InRemain == 0 && _aux2CanRead

    @inline
    private[this] def shouldComplete: Boolean = _xyEnded && !isAvailable(shape.in3) && !isAvailable(shape.in4)

    @tailrec
    def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (mainShouldRead) {
        _mainInRemain = readMainIns()
        mainInOff     = 0
        stateChange   = true
      }

      if (aux1ShouldRead) {
        aux1InRemain  = readAux1Ins()
        aux1InOff     = 0
        stateChange   = true
      }

      if (aux2ShouldRead) {
        aux2InRemain  = readAux2Ins()
        aux2InOff     = 0
        stateChange   = true
      }

      if (outSent) {
        outRemain     = allocOutputBuffers()
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      if (inValid && processChunk()) stateChange = true

      val flushOut = shouldComplete
      if (!outSent && (outRemain == 0 || flushOut) && canWrite) {
        writeOuts(outOff)
        outSent     = true
        stateChange = true
      }

      if (flushOut && outSent) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }

    // either fill image buffer (`true`) or scan buffer (`false`)
    private[this] var modeWrite = true

    @inline
    private[this] def canWriteToWindow = modeWrite && inValid //  = readFromWinRemain == 0 && inValid

    @inline
    private[this] def canReadFromWindow = !modeWrite && !isNextWindow // = readFromWinRemain > 0

    private def processChunk(): Boolean = {
      var stateChange = false

      if (canWriteToWindow) {
        if (isNextWindow) {
          writeToWinRemain  = pullWindowParams(aux1InOff)
          isNextWindow      = false
          stateChange       = true
          aux1InOff        += 1
          aux1InRemain     -= 1
        }

        val chunk = min(writeToWinRemain, _mainInRemain)
        if (chunk > 0) {
          copyInputToWindow(writeToWinOff = writeToWinOff, chunk = chunk)
          mainInOff        += chunk
          _mainInRemain    -= chunk
          writeToWinOff    += chunk
          writeToWinRemain -= chunk
          stateChange       = true

          if (writeToWinRemain == 0) {
            writeToWinOff = 0
            stateChange   = true
            modeWrite     = false
          }
        }
      }

      if (canReadFromWindow) {
        val chunk = min(outRemain, aux2InRemain)
        if (chunk > 0) {
          val chunkOut  = processWindowToOutput(outOff = outOff, chunk = chunk)
          outOff       += chunkOut
          outRemain    -= chunkOut
          aux2InOff    += chunkOut
          aux2InRemain -= chunkOut
          stateChange   = true
        }
      }

      stateChange
    }

    private def copyInputToWindow(writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, mainInOff, winBuf, writeToWinOff, chunk)

    private def processWindowToOutput(outOff: Int, chunk: Int): Int = {
      var outOffI     = outOff
      val outStop     = outOffI + chunk
      val out         = bufOut0.buf
      var _aux2InOff  = aux2InOff
      var _x          = x
      var _y          = y

      while (outOffI < outStop) {
        // the `next` input is polled with the other
        // aux1 inputs, such as `x`, `y`. If next is triggered,
        // we need to abort `processWindowToOutput` even before `chunk`
        // is exhausted. The buffer offset for aux1 therefore must
        // not be advanced (`outOffI += 1` is avoided). In order
        // for `processWindowToOutput` to be able to resume, we must
        // then "skip" the next-input for one sample; we do that
        // by looking at the `wasNextWindow` flag
        if (bufNext != null && _aux2InOff < bufNext.size) {
          if (wasNextWindow) {
            wasNextWindow = false
          } else {
            val next: Int = bufNext.buf(_aux2InOff)
            if (next != 0) {
              isNextWindow  = true
              wasNextWindow = true
              modeWrite     = true
              x = _x
              y = _y
              return outOffI - outOff
            }
          }
        }
        if (bufX != null && _aux2InOff < bufX.size) {
          _x = bufX.buf(_aux2InOff)
        }
        if (bufY != null && _aux2InOff < bufY.size) {
          _y = bufY.buf(_aux2InOff)
        }
        pullInterpParams(_aux2InOff)

        out(outOffI) = calcValue(_x, _y)

        outOffI    += 1
        _aux2InOff += 1
      }

      x = _x
      y = _y
      chunk
    }
  }
}