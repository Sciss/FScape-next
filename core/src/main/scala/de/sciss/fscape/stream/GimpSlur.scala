/*
 *  GimpSlur.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape8}
import de.sciss.fscape.stream.impl.{DemandFilterLogic, DualAuxWindowedLogic, NodeImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

object GimpSlur {
  def apply(in: OutD, width: OutI, height: OutI, kernel: OutD, kernelWidth: OutI, kernelHeight: OutI,
            repeat: OutI, wrap: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in          , stage.in0)
    b.connect(width       , stage.in1)
    b.connect(height      , stage.in2)
    b.connect(kernel      , stage.in3)
    b.connect(kernelWidth , stage.in4)
    b.connect(kernelHeight, stage.in5)
    b.connect(repeat      , stage.in6)
    b.connect(wrap        , stage.in7)
    stage.out
  }

  private final val name = "GimpSlur"

  private type Shape = FanInShape8[BufD, BufI, BufI, BufD, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
      in0 = InD (s"$name.in"          ),
      in1 = InI (s"$name.width"       ),
      in2 = InI (s"$name.height"      ),
      in3 = InD (s"$name.kernel"      ),
      in4 = InI (s"$name.kernelWidth" ),
      in5 = InI (s"$name.kernelHeight"),
      in6 = InI (s"$name.repeat"      ),
      in7 = InI (s"$name.wrap"        ),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DualAuxWindowedLogic[Shape]
      with DemandFilterLogic[BufD, Shape]
      with Out1LogicImpl[BufD, Shape]
      with Out1DoubleImpl[Shape] {

    private[this] var width       : Int     = _
    private[this] var height      : Int     = _
    private[this] var kernelWidth : Int     = _
    private[this] var kernelHeight: Int     = _
    private[this] var repeat      : Int     = _
    private[this] var wrap        : Boolean = _
    private[this] var kernelSize  : Int     = _

    private[this] var imageSize = 0
    private[this] var winBuf    : Array[Double] = _
    private[this] var kernelBuf : Array[Double] = _

    protected def startNextWindow(): Long = {
      val inOff       = aux1InOff
      val oldKernelW  = kernelWidth
      val oldKernelH  = kernelHeight
      if (bufIn1 != null && inOff < bufIn1.size) {
        width = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        height = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        kernelWidth = math.max(1, bufIn4.buf(inOff))
      }
      if (bufIn5 != null && inOff < bufIn5.size) {
        kernelHeight = math.max(1, bufIn5.buf(inOff))
      }
      if (bufIn6 != null && inOff < bufIn6.size) {
        repeat = math.max(1, bufIn6.buf(inOff))
      }
      if (bufIn7 != null && inOff < bufIn7.size) {
        wrap = bufIn7.buf(inOff) != 0
      }

      if (kernelWidth != oldKernelW || kernelHeight != oldKernelH) {
        kernelSize    = kernelWidth * kernelHeight
        kernelBuf     = new Array[Double](kernelSize)
        hasKernel     = false
        kernelRemain  = kernelSize
//
////      } else if (isInAvailable(in3) || !isClosed(in3)) {
//      } else if (bufIn3 != null && inOff < bufIn3.size) {
//        kernelRemain  = kernelSize
      }

      val newSizeOuter = width * height
      if (newSizeOuter != imageSize) {
        imageSize = newSizeOuter
        winBuf    = new Array(newSizeOuter)
      }

      imageSize
    }

    protected def processAux2(): Unit = ???

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, mainInOff, winBuf, writeToWinOff.toInt, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
//      val steps   = numColSteps.toLong * numRowSteps
//      val frames  = steps * kernelSize
//      if (frames > 0x7FFFFFFF) sys.error(s"Matrix too large - $frames frames is larger than 32bit")
//      frames.toInt
      ???
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      ???
    }

    // ---- DemandWindowedLogic adaptation ----

    private[this] var writeToWinOff     = 0L
    private[this] var writeToWinRemain  = 0L
    private[this] var readFromWinOff    = 0L
    private[this] var readFromWinRemain = 0L
    private[this] var isNextWindow      = true
    private[this] var hasKernel         = false
    private[this] var kernelRemain      = 0

    @inline
    private[this] def canWriteToWindow  = readFromWinRemain == 0 && inValid

//    protected def processChunk(): Boolean = {
//      var stateChange = false
//
//      if (canWriteToWindow) {
//        val flushIn0 = inputsEnded // inRemain == 0 && shouldComplete()
//        if (isNextWindow && !flushIn0) {
//          writeToWinRemain  = startNextWindow()
//          isNextWindow      = false
//          stateChange       = true
//          // logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
//        }
//
//        if (kernelRemain > 0 && ) {
//
//        }
//
//        val chunk     = math.min(writeToWinRemain, mainInRemain).toInt
//        val flushIn   = flushIn0 && writeToWinOff > 0
//        if (chunk > 0 || flushIn) {
//          // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
//          if (chunk > 0) {
//            copyInputToWindow(writeToWinOff = writeToWinOff, chunk = chunk)
//            mainInOff        += chunk
//            mainInRemain     -= chunk
//            writeToWinOff    += chunk
//            writeToWinRemain -= chunk
//            stateChange       = true
//          }
//
//          if ((writeToWinRemain == 0 || flushIn) && kernelRemain == 0) {
//            readFromWinRemain = processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
//            writeToWinOff     = 0
//            readFromWinOff    = 0
//            isNextWindow      = true
//            stateChange       = true
//            auxInOff         += 1
//            auxInRemain      -= 1
//            // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
//          }
//        }
//      }
//
//      if (readFromWinRemain > 0) {
//        val chunk = math.min(readFromWinRemain, outRemain).toInt
//        if (chunk > 0) {
//          // logStream(s"readFromWindow(); readFromWinOff = $readFromWinOff, outOff = $outOff, chunk = $chunk")
//          copyWindowToOutput(readFromWinOff = readFromWinOff, outOff = outOff, chunk = chunk)
//          readFromWinOff    += chunk
//          readFromWinRemain -= chunk
//          outOff            += chunk
//          outRemain         -= chunk
//          stateChange        = true
//        }
//      }
//
//      stateChange
//    }

//    protected def shouldComplete(): Boolean = inputsEnded && writeToWinOff == 0 && readFromWinRemain == 0

    // ---- bla ----

    protected     var bufIn0 : BufD = _
    private[this] var bufIn1 : BufI = _
    private[this] var bufIn2 : BufI = _
    private[this] var bufIn3 : BufD = _
    private[this] var bufIn4 : BufI = _
    private[this] var bufIn5 : BufI = _
    private[this] var bufIn6 : BufI = _
    private[this] var bufIn7 : BufI = _
    protected     var bufOut0: BufD = _

    protected def in0: InD = shape.in0
    protected def in1: InI = shape.in1
    protected def in2: InI = shape.in2
    protected def in3: InD = shape.in3
    protected def in4: InI = shape.in4
    protected def in5: InI = shape.in5
    protected def in6: InI = shape.in6
    protected def in7: InI = shape.in7

    private[this] var _mainCanRead  = false
    private[this] var _aux1CanRead  = false
    private[this] var _aux2CanRead  = false
    private[this] var _mainInValid  = false
    private[this] var _aux1InValid  = false
    private[this] var _aux2InValid  = false
    private[this] var _inValid      = false

    protected def out0: OutD = shape.out

    def mainCanRead : Boolean = _mainCanRead
    def aux1CanRead : Boolean = _aux1CanRead
    def aux2CanRead : Boolean = _aux2CanRead
    def mainInValid : Boolean = _mainInValid
    def aux1InValid : Boolean = _aux1InValid
    def aux2InValid : Boolean = _aux2InValid
    def inValid     : Boolean = _inValid

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
    }

    override protected def stopped(): Unit = {
      freeInputBuffers()
      freeOutputBuffers()
      winBuf    = null
      kernelBuf = null
    }

    protected def readMainIns(): Int = {
      freeMainInBuffers()
      val sh        = shape
      bufIn0        = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      if (!_mainInValid) {
        _mainInValid= true
        _inValid    = _aux1InValid
      }

      _mainCanRead = false
      bufIn0.size
    }

    protected def readAux1Ins(): Int = {
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
        sz      = math.max(sz, bufIn2.size)
        tryPull(sh.in2)
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

      if (!_aux1InValid) {
        _aux1InValid = true
        _inValid    = _mainInValid && _aux2InValid
      }

      _aux1CanRead = false
      sz
    }

    protected def readAux2Ins(): Int = {
      freeAux2InBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in3)) {
        bufIn3  = grab(sh.in3)
        sz      = bufIn3.size
        tryPull(sh.in3)
      }

      if (!_aux2InValid) {
        _aux2InValid = true
        _inValid    = _mainInValid && _aux1InValid
      }

      _aux2CanRead = false
      sz
    }

    protected def freeInputBuffers(): Unit = {
      freeMainInBuffers()
      freeAux1InBuffers()
      freeAux2InBuffers()
    }

    private def freeMainInBuffers(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
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

    private def freeAux2InBuffers(): Unit =
      if (bufIn3 != null) {
        bufIn3.release()
        bufIn3 = null
      }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    def updateMainCanRead(): Unit =
      _mainCanRead = isAvailable(in0)

    def updateAux1CanRead(): Unit = {
      val sh = shape
      _aux1CanRead =
        ((isClosed(sh.in1) && _aux1InValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _aux1InValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in4) && _aux1InValid) || isAvailable(sh.in4)) &&
        ((isClosed(sh.in5) && _aux1InValid) || isAvailable(sh.in5)) &&
        ((isClosed(sh.in6) && _aux1InValid) || isAvailable(sh.in6)) &&
        ((isClosed(sh.in7) && _aux1InValid) || isAvailable(sh.in7))
    }

    def updateAux2CanRead(): Unit = {
      val sh = shape
      _aux2CanRead = (isClosed(sh.in3) && _aux2InValid) || isAvailable(sh.in3)
    }

    ???
//    new DemandProcessInHandler(shape.in0, this)
//    new DemandAuxInHandler    (shape.in1, this)
//    new DemandAuxInHandler    (shape.in2, this)
//    new DemandAuxInHandler    (shape.in3, this)
//    new DemandAuxInHandler    (shape.in4, this)
//    new DemandAuxInHandler    (shape.in5, this)
//    new DemandAuxInHandler    (shape.in6, this)
//    new DemandAuxInHandler    (shape.in7, this)
    new ProcessOutHandlerImpl (shape.out, this)
  }
}