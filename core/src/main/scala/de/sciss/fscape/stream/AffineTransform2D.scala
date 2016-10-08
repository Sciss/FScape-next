/*
 *  AffineTransform2D.scala
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

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape15}
import de.sciss.fscape.stream.impl.{DemandFilterLogic, NodeImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}
import de.sciss.numbers.IntFunctions

import scala.annotation.tailrec
import math.{max, min}

object AffineTransform2D {
  def apply(in: OutD, widthIn: OutI, heightIn: OutI, widthOut: OutI, heightOut: OutI,
            m00: OutD, m10: OutD, m01: OutD, m11: OutD, m02: OutD, m12: OutD, wrap: OutI,
            rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in            , stage.in0)
    b.connect(widthIn       , stage.in1)
    b.connect(heightIn      , stage.in2)
    b.connect(widthOut      , stage.in3)
    b.connect(heightOut     , stage.in4)
    b.connect(m00           , stage.in5)
    b.connect(m10           , stage.in6)
    b.connect(m01           , stage.in7)
    b.connect(m11           , stage.in8)
    b.connect(m02           , stage.in9)
    b.connect(m12           , stage.in10)
    b.connect(wrap          , stage.in11)
    b.connect(rollOff       , stage.in12)
    b.connect(kaiserBeta    , stage.in13)
    b.connect(zeroCrossings , stage.in14)
    stage.out
  }

  private final val name = "AffineTransform2D"

  private type Shape = FanInShape15[BufD, BufI, BufI, BufI, BufI, BufD, BufD, BufD, BufD, BufD, BufD, BufI, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape15(
      in0  = InD (s"$name.in"),
      in1  = InI (s"$name.widthIn"),
      in2  = InI (s"$name.heightIn"),
      in3  = InI (s"$name.widthOut"),
      in4  = InI (s"$name.heightOut"),
      in5  = InD (s"$name.m00"),
      in6  = InD (s"$name.m10"),
      in7  = InD (s"$name.m01"),
      in8  = InD (s"$name.m11"),
      in9  = InD (s"$name.m02"),
      in10 = InD (s"$name.m12"),
      in11 = InI (s"$name.wrap"),
      in12 = InD (s"$name.rollOff"),
      in13 = InD (s"$name.kaiserBeta"),
      in14 = InI (s"$name.zeroCrossings"),
      out  = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandFilterLogic[BufD, Shape]
      with Out1LogicImpl[BufD, Shape]
      with Out1DoubleImpl[Shape] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var widthIn   : Int = _
    private[this] var heightIn  : Int = _
    private[this] var widthOut  : Int = _
    private[this] var heightOut : Int = _

    /*

      All window defining parameters (`widthIn`, `heightIn`, `widthOut`, `heightOut`)
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
    private[this] var readFromWinOff    = 0
    private[this] var readFromWinRemain = 0
    private[this] var isNextWindow      = true

    protected def mainInRemain: Int = _mainInRemain

    protected     var bufIn0 : BufD = _   // in
    
    private[this] var bufIn1 : BufI = _   // widthIn
    private[this] var bufIn2 : BufI = _   // heightIn
    private[this] var bufIn3 : BufI = _   // widthOut
    private[this] var bufIn4 : BufI = _   // heightOut
    
    private[this] var bufIn5 : BufD = _   // m00
    private[this] var bufIn6 : BufD = _   // m10
    private[this] var bufIn7 : BufD = _   // m01
    private[this] var bufIn8 : BufD = _   // m11
    private[this] var bufIn9 : BufD = _   // m02
    private[this] var bufIn10: BufD = _   // m12
    private[this] var bufIn11: BufI = _   // wrap
    private[this] var bufIn12: BufD = _   // rollOff
    private[this] var bufIn13: BufD = _   // kaiserBeta
    private[this] var bufIn14: BufI = _   // zeroCrossings

    protected     var bufOut0: BufD = _

    private[this] var _inValid = false

    def inValid: Boolean = _inValid

    override def preStart(): Unit =
      shape.inlets.foreach(pull(_))

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
    }

    private def freeInputBuffers(): Unit = {
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
        } // may lead to `flushOut`
        else {
          if (!isAvailable(shape.in0)) {
            println(s"Invalid process ${shape.in0}")
            completeStage()
          }
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
        ((isClosed(sh.in2) && _aux1InValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _aux1InValid) || isAvailable(sh.in3)) &&
        ((isClosed(sh.in4) && _aux1InValid) || isAvailable(sh.in4))
    }

    private def freeAux1InBuffers(): Unit = {
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
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
    }

    private def readAux1Ins(): Int = {
      freeAux1InBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in1)) {
        bufIn1  = grab(sh.in1)
        sz      = bufIn1.size
        tryPull(sh.in1)
      }
      if (isAvailable(sh.in2)) {
        bufIn2  = grab(sh.in2)
        sz      = max(sz, bufIn2.size)
        tryPull(sh.in2)
      }
      if (isAvailable(sh.in3)) {
        bufIn3  = grab(sh.in3)
        sz      = max(sz, bufIn3.size)
        tryPull(sh.in3)
      }
      if (isAvailable(sh.in4)) {
        bufIn4  = grab(sh.in4)
        sz      = max(sz, bufIn4.size)
        tryPull(sh.in4)
      }

      if (!_aux1InValid) {
        _aux1InValid  = true
        _inValid      = _mainInValid && _aux2InValid 
      }

      _aux1CanRead = false
      sz
    }
    
    // `widthIn`, `heightIn`, `widthOut`, `heightOut` are per-window auxiliary inputs
    (1 to 4).foreach { inIdx =>
      val in = shape.inlets(inIdx)
      setHandler(in, new InHandler {
        def onPush(): Unit = {
          logStream(s"onPush($in)")
          testRead()
        }

        private[this] def testRead(): Unit = {
          updateAux1CanRead()
          if (_aux1CanRead) process()
        }

        override def onUpstreamFinish(): Unit = {
          logStream(s"onUpstreamFinish($in)")
          if (_aux1InValid || isAvailable(in)) {
            testRead()
          } else {
            println(s"Invalid aux $in")
            completeStage()
          }
        }
      })
    }

    // -------- aux (ongoing output) input ---------

    private[this] var _aux2InValid  = false
    private[this] var _aux2CanRead  = false

    private def updateAux2CanRead(): Unit = {
      val sh = shape
      _aux2CanRead =
        ((isClosed(sh.in5 ) && _aux2InValid) || isAvailable(sh.in5 )) &&
        ((isClosed(sh.in6 ) && _aux2InValid) || isAvailable(sh.in6 )) &&
        ((isClosed(sh.in7 ) && _aux2InValid) || isAvailable(sh.in7 )) &&
        ((isClosed(sh.in8 ) && _aux2InValid) || isAvailable(sh.in8 )) &&
        ((isClosed(sh.in9 ) && _aux2InValid) || isAvailable(sh.in9 )) &&
        ((isClosed(sh.in10) && _aux2InValid) || isAvailable(sh.in10)) &&
        ((isClosed(sh.in11) && _aux2InValid) || isAvailable(sh.in11)) &&
        ((isClosed(sh.in12) && _aux2InValid) || isAvailable(sh.in12)) &&
        ((isClosed(sh.in13) && _aux2InValid) || isAvailable(sh.in13)) &&
        ((isClosed(sh.in14) && _aux2InValid) || isAvailable(sh.in14))
    }

    private def freeAux2InBuffers(): Unit = {
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
      if (bufIn10 != null) {
        bufIn10.release()
        bufIn10 = null
      }
      if (bufIn11 != null) {
        bufIn11.release()
        bufIn11 = null
      }
      if (bufIn12 != null) {
        bufIn12.release()
        bufIn12 = null
      }
      if (bufIn13 != null) {
        bufIn13.release()
        bufIn13 = null
      }
      if (bufIn14 != null) {
        bufIn14.release()
        bufIn14 = null
      }
    }

    private def readAux2Ins(): Int = {
      freeAux2InBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in5)) {
        bufIn5  = grab(sh.in5)
        sz      = bufIn5.size
        tryPull(sh.in5)
      }
      if (isAvailable(sh.in6)) {
        bufIn6  = grab(sh.in6)
        sz      = max(sz, bufIn6.size)
        tryPull(sh.in6)
      }
      if (isAvailable(sh.in7)) {
        bufIn7  = grab(sh.in7)
        sz      = max(sz, bufIn7.size)
        tryPull(sh.in7)
      }
      if (isAvailable(sh.in8)) {
        bufIn8  = grab(sh.in8)
        sz      = max(sz, bufIn8.size)
        tryPull(sh.in8)
      }
      if (isAvailable(sh.in9)) {
        bufIn9  = grab(sh.in9)
        sz      = max(sz, bufIn9.size)
        tryPull(sh.in9)
      }
      if (isAvailable(sh.in10)) {
        bufIn10 = grab(sh.in10)
        sz      = max(sz, bufIn10.size)
        tryPull(sh.in10)
      }
      if (isAvailable(sh.in11)) {
        bufIn11 = grab(sh.in11)
        sz      = max(sz, bufIn11.size)
        tryPull(sh.in11)
      }
      if (isAvailable(sh.in12)) {
        bufIn12 = grab(sh.in12)
        sz      = max(sz, bufIn12.size)
        tryPull(sh.in12)
      }
      if (isAvailable(sh.in13)) {
        bufIn13 = grab(sh.in13)
        sz      = max(sz, bufIn13.size)
        tryPull(sh.in13)
      }
      if (isAvailable(sh.in14)) {
        bufIn14 = grab(sh.in14)
        sz      = max(sz, bufIn14.size)
        tryPull(sh.in14)
      }

      if (!_aux2InValid) {
        _aux2InValid  = true
        _inValid      = _mainInValid && _aux1InValid
      }

      _aux2CanRead = false
      sz
    }

    // the matrix and filter inputs are output driven
    (5 to 14).foreach { inIdx =>
      val in = shape.inlets(inIdx)
      setHandler(in, new InHandler {
        def onPush(): Unit = {
          logStream(s"onPush($in)")
          testRead()
        }

        private[this] def testRead(): Unit = {
          updateAux2CanRead()
          if (_aux2CanRead) process()
        }

        override def onUpstreamFinish(): Unit = {
          logStream(s"onUpstreamFinish($in)")
          if (_aux2InValid || isAvailable(in)) {
            testRead()
          } else {
            println(s"Invalid aux $in")
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
    private[this] def shouldComplete = inputsEnded && writeToWinOff == 0 && readFromWinRemain == 0

    @tailrec
    def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (mainShouldRead) {
        _mainInRemain = readMainIns()
        mainInOff    = 0
        stateChange   = true
      }

      if (aux1ShouldRead) {
        aux1InRemain = readAux1Ins()
        aux1InOff    = 0
        stateChange   = true
      }

      if (aux2ShouldRead) {
        aux2InRemain = readAux2Ins()
        aux2InOff    = 0
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

    @inline
    private[this] def canWriteToWindow  = readFromWinRemain == 0 && inValid

    @inline
    private[this] def canReadFromWindow = readFromWinRemain > 0

    private def processChunk(): Boolean = {
      var stateChange = false

      if (canWriteToWindow) {
        val flushIn0 = inputsEnded // inRemain == 0 && shouldComplete()
        if (isNextWindow && !flushIn0) {
          writeToWinRemain  = startNextWindow()
          isNextWindow      = false
          stateChange       = true
          aux1InOff        += 1
          aux1InRemain     -= 1
          // logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
        }

        val chunk     = min(writeToWinRemain, _mainInRemain)
        val flushIn   = flushIn0 && writeToWinOff > 0
        if (chunk > 0 || flushIn) {
          // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
          if (chunk > 0) {
            copyInputToWindow(writeToWinOff = writeToWinOff, chunk = chunk, isFlush = flushIn)
            mainInOff        += chunk
            _mainInRemain    -= chunk
            writeToWinOff    += chunk
            writeToWinRemain -= chunk
            stateChange       = true
          }

          if (writeToWinRemain == 0 || flushIn) {
            // readFromWinRemain = processWindow(writeToWinOff = writeToWinOff)
            readFromWinRemain = widthOut * heightOut
            writeToWinOff     = 0
            readFromWinOff    = 0
            // isNextWindow      = true
            stateChange       = true
            // auxInOff         += 1
            // auxInRemain      -= 1
            // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
          }
        }
      }

      if (canReadFromWindow) {
        val chunk = min(readFromWinRemain, outRemain)
        if (chunk > 0) {
          // logStream(s"readFromWindow(); readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk")
          processWindowToOutput(imgOutOff = readFromWinOff, outOff = outOff, chunk = chunk)
          readFromWinOff    += chunk
          readFromWinRemain -= chunk
          outOff            += chunk
          outRemain         -= chunk
          aux2InOff         += chunk
          aux2InRemain      -= chunk
          stateChange        = true
        }
      }

      stateChange
    }

    private def startNextWindow(): Int = {
      var newImageIn  = false
      val inOff       = aux1InOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        val value = max(1, bufIn1.buf(inOff))
        if (widthIn != value) {
          widthIn     = value
          newImageIn  = true
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val value = max(1, bufIn2.buf(inOff))
        if (heightIn != value) {
          heightIn    = value
          newImageIn  = true
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        widthOut = max(1, bufIn3.buf(inOff))
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        heightOut = max(1, bufIn4.buf(inOff))
      }
      if (newImageIn) {
        winBuf = new Array[Double](widthIn * heightIn)
      }
      winBuf.length
    }

    private def copyInputToWindow(writeToWinOff: Int, chunk: Int, isFlush: Boolean): Unit = {
      Util.copy(bufIn0.buf, mainInOff, winBuf, writeToWinOff, chunk)
      if (isFlush) {
        val off1 = writeToWinOff + chunk
        Util.clear(winBuf, off1, winBuf.length - off1)
      }
    }

    private[this] var mi00   = 0.0
    private[this] var mi10   = 0.0
    private[this] var mi01   = 0.0
    private[this] var mi11   = 0.0
    private[this] var mi02   = 0.0
    private[this] var mi12   = 0.0

    private[this] var m00   = 0.0
    private[this] var m10   = 0.0
    private[this] var m01   = 0.0
    private[this] var m11   = 0.0
    private[this] var m02   = 0.0
    private[this] var m12   = 0.0

    private[this] var rollOff       = -1.0  // must be negative for init detection
    private[this] var kaiserBeta    = -1.0
    private[this] var zeroCrossings = 0
    private[this] var wrapBounds    = false

    private[this] var fltLenH     : Int           = _
    private[this] var fltBuf      : Array[Double] = _
    private[this] var fltBufD     : Array[Double] = _
    private[this] var fltGain     : Double        = _

    private[this] val fltSmpPerCrossing = 4096

    // calculates low-pass filter kernel
    @inline
    private[this] def updateTable(): Unit = {
      fltLenH = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
      fltBuf  = new Array[Double](fltLenH)
      fltBufD = new Array[Double](fltLenH)
      fltGain = Filter.createAntiAliasFilter(
        fltBuf, fltBufD, halfWinSize = fltLenH, samplesPerCrossing = fltSmpPerCrossing, rollOff = rollOff,
        kaiserBeta = kaiserBeta)
    }

    // calculates inverted matrix
    @inline
    private[this] def updateMatrix(): Unit = {
      val det = mi00 * mi11 - mi01 * mi10
      m00 =  mi11 / det
      m10 = -mi10 / det
      m01 = -mi01 / det
      m11 =  mi00 / det
      m02 = (mi01 * mi12 - mi11 * mi02) / det
      m12 = (mi10 * mi02 - mi00 * mi12) / det
    }

    private def processWindowToOutput(imgOutOff: Int, outOff: Int, chunk: Int): Unit = {
      var outOffI     = outOff
      val outStop     = outOffI + chunk
      val out         = bufOut0.buf
      val _widthIn    = widthIn
      val _heightIn   = heightIn
      val _widthOut   = widthOut
      var _m00        = m00
      var _m10        = m10
      var _m01        = m01
      var _m11        = m11
      var _m02        = m02
      var _m12        = m12
      var _aux2InOff  = aux2InOff
      var _wrap       = wrapBounds
      val _winBuf     = winBuf
      var newTable    = false
      var newMatrix   = false

      var x = imgOutOff % _widthOut
      var y = imgOutOff / _widthOut

      while (outOffI < outStop) {
        if (bufIn5 != null && _aux2InOff < bufIn5.size) {
          val value = bufIn5.buf(_aux2InOff)
          if (mi00 != value) {
            mi00      = value
            newMatrix = true
          }
        }
        if (bufIn6 != null && _aux2InOff < bufIn6.size) {
          val value = bufIn6.buf(_aux2InOff)
          if (mi10 != value) {
            mi10      = value
            newMatrix = true
          }
        }
        if (bufIn7 != null && _aux2InOff < bufIn7.size) {
          val value = bufIn7.buf(_aux2InOff)
          if (mi01 != value) {
            mi01      = value
            newMatrix = true
          }
        }
        if (bufIn8 != null && _aux2InOff < bufIn8.size) {
          val value = bufIn8.buf(_aux2InOff)
          if (mi11 != value) {
            mi11      = value
            newMatrix = true
          }
        }
        if (bufIn9 != null && _aux2InOff < bufIn9.size) {
          val value = bufIn9.buf(_aux2InOff)
          if (mi02 != value) {
            mi02      = value
            newMatrix = true
          }
        }
        if (bufIn10 != null && _aux2InOff < bufIn10.size) {
          val value = bufIn10.buf(_aux2InOff)
          if (mi12 != value) {
            mi12      = value
            newMatrix = true
          }
        }

        if (bufIn11 != null && _aux2InOff < bufIn11.size) {
          wrapBounds  = bufIn11.buf(_aux2InOff) != 0
          _wrap       = wrapBounds
        }

        if (bufIn12 != null && _aux2InOff < bufIn12.size) {
          val newRollOff = max(0.0, min(1.0, bufIn12.buf(_aux2InOff)))
          if (rollOff != newRollOff) {
            rollOff   = newRollOff
            newTable  = true
          }
        }

        if (bufIn13 != null && _aux2InOff < bufIn13.size) {
          val newKaiserBeta = max(0.0, bufIn13.buf(_aux2InOff))
          if (kaiserBeta != newKaiserBeta) {
            kaiserBeta  = newKaiserBeta
            newTable    = true
          }
        }

        if (bufIn14 != null && _aux2InOff < bufIn14.size) {
          val newZeroCrossings = max(1, bufIn14.buf(_aux2InOff))
          if (zeroCrossings != newZeroCrossings) {
            zeroCrossings = newZeroCrossings
            newTable      = true
          }
        }

        if (newMatrix) {
          updateMatrix()
          _m00 = m00
          _m10 = m10
          _m01 = m01
          _m11 = m11
          _m02 = m02
          _m12 = m12
          newMatrix = false
        }
        
        // [ x']   [  m00  m01  m02  ] [ x ]   [ m00x + m01y + m02 ]
        // [ y'] = [  m10  m11  m12  ] [ y ] = [ m10x + m11y + m12 ]
        // [ 1 ]   [   0    0    1   ] [ 1 ]   [         1         ]

        val xT = _m00 * x + _m01 * y + _m02
        val yT = _m10 * x + _m11 * y + _m12

        if (newTable) {
          updateTable()
          newTable = false
        }

        val _winLen   = _winBuf.length
        val _inPhase  = xT
        val factor    = _m00
        val factorMn1 = min(1.0, factor)
        val _fltIncr  = fltSmpPerCrossing * factorMn1
        val gain      = fltGain * factorMn1
        val _fltBuf   = fltBuf
        val _fltBufD  = fltBufD
        val _fltLenH  = fltLenH
        val q         = _inPhase % 1.0
        var value     = 0.0
        val _inPhaseI = _inPhase.toInt

        // left-hand side of window
        var srcOffI   = _inPhaseI
        var fltOff    = q * _fltIncr
        var fltOffI   = fltOff.toInt
        var srcRem    = if (_wrap) Int.MaxValue else srcOffI
        srcOffI       = IntFunctions.wrap(srcOffI, 0, _widthIn - 1)
        while ((fltOffI < _fltLenH) && (srcRem > 0)) {
          val r    = fltOff % 1.0  // 0...1 for interpol.
          val w    = _fltBuf(fltOffI) + _fltBufD(fltOffI) * r
          val Y_CLIP  = IntFunctions.wrap(yT.toInt, 0, _heightIn - 1)
          val OFF     = Y_CLIP * _widthIn + srcOffI
          value   += winBuf(OFF) * w
          srcOffI -= 1
          if (srcOffI < 0) srcOffI += _widthIn
          srcRem  -= 1
          fltOff  += _fltIncr
          fltOffI  = fltOff.toInt
        }

        // right-hand side of window
        srcOffI       = (_inPhaseI + 1) % _winLen
        fltOff        = (1.0 - q) * _fltIncr
        fltOffI       = fltOff.toInt
        srcRem        = if (_wrap) Int.MaxValue else _widthIn - srcOffI
        srcOffI       = IntFunctions.wrap(srcOffI, 0, _widthIn - 1)
        while ((fltOffI < _fltLenH) && (srcRem > 0)) {
          val r    = fltOff % 1.0  // 0...1 for interpol.
          val w    = _fltBuf(fltOffI) + _fltBufD(fltOffI) * r
          val Y_CLIP  = IntFunctions.wrap(yT.toInt, 0, _heightIn - 1)
          val OFF     = Y_CLIP * _widthIn + srcOffI
          value   += winBuf(OFF) * w
          srcOffI += 1
          if (srcOffI == _widthIn) srcOffI = 0
          srcRem  -= 1
          fltOff  += _fltIncr
          fltOffI  = fltOff.toInt
        }

        out(outOffI) = value * gain
        // inPhaseCount   += 1
        // readFromWinLen -= 1

        outOffI    += 1
        _aux2InOff += 1
        x          += 1
        if (x == _widthOut) {
          x  = 0
          y += 1
        }
      }
    }
  }
}