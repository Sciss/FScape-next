/*
 *  GimpSlur.scala
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

import java.util

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape8, Inlet}
import de.sciss.fscape.stream.impl.deprecated.{DemandFilterLogic, DualAuxWindowedLogic, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.IntFunctions

import scala.util.Random

object GimpSlur {
  def apply(in: OutD, width: OutI, height: OutI, kernel: OutD, kernelWidth: OutI, kernelHeight: OutI,
            repeat: OutI, wrap: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
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

  private type Shp = FanInShape8[BufD, BufI, BufI, BufD, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape8(
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

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DualAuxWindowedLogic   [Shp]
      with DemandFilterLogic[BufD, Shp]
      with Out1LogicImpl    [BufD, Shp]
      with Out1DoubleImpl         [Shp] {

    logic =>

    private[this] var width       : Int     = _
    private[this] var height      : Int     = _
    private[this] var kernelWidth : Int     = _
    private[this] var kernelHeight: Int     = _
    private[this] var repeat      : Int     = _
    private[this] var wrap        : Boolean = _
    private[this] var kernelSize  : Int     = _

    private[this] var imageSize = 0
    private[this] var winBuf1   : Array[Double] = _
    private[this] var winBuf2   : Array[Double] = _
    private[this] var kernelBuf : Array[Double] = _

    private[this] val rnd: Random = ctrl.mkRandom()

    // ---- DemandWindowedLogic adaptation ----

    private[this] var hasKernel         = false
    private[this] var kernelRemain      = 0

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

      val needsKernel = if (kernelWidth != oldKernelW || kernelHeight != oldKernelH) {
        kernelSize    = kernelWidth * kernelHeight
        kernelBuf     = new Array[Double](kernelSize)
        hasKernel     = false
        true

//      } else if (isInAvailable(in3) || !isClosed(in3)) {
      } else {
        !hasKernel ||
          (bufIn3 != null && aux2InOff < bufIn3.size) ||
          (isClosed(shape.in3) && isAvailable(shape.in3)) || !isClosed(shape.in3)
      }

      kernelRemain = if (needsKernel) kernelSize else 0

      val newSizeOuter = width * height
      if (newSizeOuter != imageSize) {
        imageSize   = newSizeOuter
        winBuf1     = new Array(newSizeOuter)
        winBuf2     = new Array(newSizeOuter)
      }

      imageSize
    }

    protected def processAux2(): Boolean = {
      if (kernelRemain > 0) {
        if (bufIn3 != null && aux2InOff < bufIn3.size) {
          val chunk = math.min(kernelRemain, aux2InRemain)
          Util.copy(bufIn3.buf, aux2InOff, kernelBuf, kernelSize - kernelRemain, chunk)
          aux2InOff    += chunk
          aux2InRemain -= chunk
          kernelRemain -= chunk
          if (kernelRemain == 0) hasKernel = true
        }
        if (aux2InRemain == 0 && isClosed(shape.in3) && !isAvailable(shape.in3)) {
          Util.fill(kernelBuf, kernelSize - kernelRemain, kernelRemain, Double.MaxValue)
          kernelRemain  = 0
          hasKernel     = true
        }
      }

      kernelRemain == 0
    }

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, mainInOff, winBuf1, writeToWinOff.toInt, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val w       = width
      val h       = height
      val wk      = kernelWidth
      val hk      = kernelHeight
      val cx      = wk >>> 1
      val cy      = hk >>> 1
      val szKern  = kernelSize
      var bIn     = winBuf1
      var bOut    = winBuf2
      val bKern   = kernelBuf
      val _wrap   = wrap

      var r = repeat
      while (r > 0) {
        var off = 0
        var y = 0
        while (y < h) {
          var x = 0
          while (x < w) {
            val n   = rnd.nextDouble()
            val i   = util.Arrays.binarySearch(bKern, 0, szKern, n)
            val j   = if (i >= 0) i else math.min(szKern - 1, -(i + 1))
            var xi  = (j % wk) - cx + x
            var yi  = (j / wk) - cy + y

            if (xi < 0 || xi >= w) {
              xi = if (_wrap) IntFunctions.wrap(xi, 0, w - 1) else IntFunctions.clip(xi, 0, w - 1)
            }
            if (yi < 0 || yi >= h) {
              yi = if (_wrap) IntFunctions.wrap(yi, 0, h - 1) else IntFunctions.clip(yi, 0, h - 1)
            }
            val value = bIn(yi * w + xi)
            bOut(off) = value

            off += 1
            x   += 1
          }
          y += 1
        }

        val tmp = bIn
        bIn     = bOut
        bOut    = tmp
        r -= 1
      }
      imageSize
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val b = if (repeat % 2 == 1) winBuf2 else winBuf1
      Util.copy(b, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)
    }

    private[this] var _mainCanRead  = false
    private[this] var _aux1CanRead  = false
    private[this] var _aux2CanRead  = false
    private[this] var _mainInValid  = false
    private[this] var _aux1InValid  = false
    private[this] var _aux2InValid  = false
    private[this] var _inValid      = false

    protected def in0: InD = shape.in0
    protected def in1: InI = shape.in1
    protected def in2: InI = shape.in2
    protected def in3: InD = shape.in3
    protected def in4: InI = shape.in4
    protected def in5: InI = shape.in5
    protected def in6: InI = shape.in6
    protected def in7: InI = shape.in7

    protected def out0: OutD = shape.out

    def mainCanRead : Boolean = _mainCanRead
    def aux1CanRead : Boolean = _aux1CanRead
    def aux2CanRead : Boolean = _aux2CanRead
    def mainInValid : Boolean = _mainInValid
    def aux1InValid : Boolean = _aux1InValid
    def aux2InValid : Boolean = _aux2InValid
    def inValid     : Boolean = _inValid

    override protected def stopped(): Unit = {
      freeInputBuffers()
      freeOutputBuffers()
      winBuf1   = null
      winBuf2   = null
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

    final class Aux1InHandler[A](in: Inlet[A])
      extends InHandler {

      def onPush(): Unit = {
        logStream(s"onPush($in)")
        testRead()
      }

      private[this] def testRead(): Unit = {
        logic.updateAux1CanRead()
        if (logic.aux1CanRead) logic.process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (logic.aux1InValid || logic.isInAvailable(in)) {
          testRead()
        } else {
          logStream(s"Invalid aux $in")
          logic.completeStage()
        }
      }

      logic.setInHandler(in, this)
    }


    final class Aux2InHandler[A](in: Inlet[A])
      extends InHandler {

      def onPush(): Unit = {
        logStream(s"onPush($in)")
        testRead()
      }

      private[this] def testRead(): Unit = {
        logic.updateAux2CanRead()
        if (logic.aux2CanRead) logic.process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        val hasMore = logic.isInAvailable(in)
        if (logic.aux2InValid || hasMore) {
          if (!hasMore && aux2InRemain == 0) kernelRemain = 0   // keep going with previous
          testRead()
        } else {
          logStream(s"Invalid aux $in")
          logic.completeStage()
        }
      }

      logic.setInHandler(in, this)
    }

    final class ProcessInHandler[A](in: Inlet[A])
      extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        logic.updateMainCanRead()
        if (logic.mainCanRead) logic.process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (logic.mainInValid) {
          // logic.updateCanRead()
          logic.process()
        } // may lead to `flushOut`
        else {
          if (!logic.isInAvailable(in)) {
            logStream(s"Invalid process $in")
            logic.completeStage()
          }
        }
      }

      logic.setInHandler(in, this)
    }

    new ProcessInHandler(shape.in0)
    new Aux1InHandler(shape.in1)
    new Aux1InHandler(shape.in2)
    new Aux2InHandler(shape.in3)
    new Aux1InHandler(shape.in4)
    new Aux1InHandler(shape.in5)
    new Aux1InHandler(shape.in6)
    new Aux1InHandler(shape.in7)

    new ProcessOutHandlerImpl (shape.out, this)
  }
}