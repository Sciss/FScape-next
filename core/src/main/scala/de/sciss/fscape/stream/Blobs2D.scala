/*
 *  Blobs2D.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.OutHandler
import akka.stream.{Attributes, Outlet}
import de.sciss.fscape.stream.impl.{AuxInHandlerImpl, FilterLogicImpl, FullInOutImpl, In4Out5Shape, NodeImpl, ProcessInHandlerImpl, StageImpl}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

object Blobs2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh: OutD)(implicit b: Builder): Vec[OutI] = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(width , stage.in1)
    b.connect(height, stage.in2)
    b.connect(thresh, stage.in3)
    Vector(stage.out0, stage.out1, stage.out2, stage.out3, stage.out4)
  }

  private final val name = "Blobs2D"

  private type Shape = In4Out5Shape[BufD, BufI, BufI, BufD, BufI, BufI, BufI, BufI, BufI]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = In4Out5Shape(
      in0  = InD (s"$name.in"      ),
      in1  = InI (s"$name.width"   ),
      in2  = InI (s"$name.height"  ),
      in3  = InD (s"$name.thresh"  ),
      out0 = OutI(s"$name.numBlobs"),
      out1 = OutI(s"$name.xMin"    ),
      out2 = OutI(s"$name.xMax"    ),
      out3 = OutI(s"$name.yMin"    ),
      out4 = OutI(s"$name.yMax"    )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Blob

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
    with FullInOutImpl[Shape]
    with FilterLogicImpl[BufD, Shape] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var width     : Int           = _
    private[this] var height    : Int           = _
    private[this] var thresh    : Double        = _
    private[this] var winSize   : Int           = _

    private[this] var writeToWinOff         = 0L
    private[this] var writeToWinRemain      = 0L
    private[this] var readNumBlobsRemain    = false
    private[this] var readBlobBoundsOff     = 0
    private[this] var readBlobBoundsRemain  = 0
    private[this] var isNextWindow          = true

    protected     var bufIn0 : BufD  = _
    private[this] var bufIn1 : BufI  = _
    private[this] var bufIn2 : BufI  = _
    private[this] var bufIn3 : BufD  = _
    private[this] var bufOut0: BufI = _
    private[this] var bufOut1: BufI = _
    private[this] var bufOut2: BufI = _
    private[this] var bufOut3: BufI = _
    private[this] var bufOut4: BufI = _

    protected def in0: InD = shape.in0

    private[this] var _canRead  = false
    private[this] var _inValid  = false
    private[this] var _canWrite = false

    private[this] var inOff           = 0  // regarding `bufIn`
    protected     var inRemain        = 0
    private[this] var outOff0         = 0  // regarding `bufOut0`
    private[this] var outOff1         = 0  // regarding `bufOut1` to `bufOut4`
    private[this] var outRemain0      = 0
    private[this] var outRemain1      = 0

    private[this] var outSent0        = true
    private[this] var outSent1        = true

    // result of blob analysis
    private[this] var blobs: Array[Blob] = _

    private final class OutHandlerImpl[A](out: Outlet[A])
      extends OutHandler {

      def onPull(): Unit = {
        logStream(s"onPull($out)")
        updateCanWrite()
        if (canWrite) process()
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish($out)")
        val allClosed = shape.outlets.forall(isClosed(_))
        if (allClosed) super.onDownstreamFinish()
      }

      setOutHandler(out, this)
    }

    new ProcessInHandlerImpl (shape.in0, this)
    new AuxInHandlerImpl     (shape.in1, this)
    new AuxInHandlerImpl     (shape.in2, this)
    new AuxInHandlerImpl     (shape.in3, this)
    new OutHandlerImpl       (shape.out0)
    new OutHandlerImpl       (shape.out1)
    new OutHandlerImpl       (shape.out2)
    new OutHandlerImpl       (shape.out3)
    new OutHandlerImpl       (shape.out4)

    def canRead : Boolean = _canRead
    def inValid : Boolean = _inValid
    def canWrite: Boolean = _canWrite

    def updateCanWrite(): Unit = {
      val sh = shape
      _canWrite =
        (isClosed(sh.out0) || isAvailable(sh.out0)) &&
        (isClosed(sh.out1) || isAvailable(sh.out1)) &&
        (isClosed(sh.out2) || isAvailable(sh.out2)) &&
        (isClosed(sh.out3) || isAvailable(sh.out3))
    }

    protected def writeOuts(ignore: Int): Unit = throw new UnsupportedOperationException

    private def writeOuts0(): Unit = {
      if (outOff0 > 0) {
        bufOut0.size = outOff0
        push(shape.out0, bufOut0)
      } else {
        bufOut0.release()
      }
      bufOut0   = null
      _canWrite = false
    }

    private def writeOuts1(): Unit = {
      if (outOff1 > 0) {
        bufOut1.size = outOff1
        bufOut2.size = outOff1
        bufOut3.size = outOff1
        bufOut4.size = outOff1
        push(shape.out1, bufOut1)
        push(shape.out2, bufOut2)
        push(shape.out3, bufOut3)
        push(shape.out4, bufOut4)
      } else {
        bufOut1.release()
        bufOut2.release()
        bufOut3.release()
        bufOut4.release()
      }
      bufOut1 = null
      bufOut2 = null
      bufOut3 = null
      bufOut4 = null

      _canWrite = false
    }

    override protected def stopped(): Unit = {
      winBuf      = null
      blobs       = null
      gridVisited = null

      freeInputBuffers()
      freeOutputBuffers()
    }

    protected def readIns(): Int = {
      freeInputBuffers()
      val sh    = shape
      bufIn0    = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      if (isAvailable(sh.in1)) {
        bufIn1 = grab(sh.in1)
        tryPull(sh.in1)
      }

      if (isAvailable(sh.in2)) {
        bufIn2 = grab(sh.in2)
        tryPull(sh.in2)
      }

      if (isAvailable(sh.in3)) {
        bufIn3 = grab(sh.in3)
        tryPull(sh.in3)
      }

      _inValid = true
      _canRead = false
      bufIn0.size
    }

    protected def freeInputBuffers(): Unit = {
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }
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
    }

    protected def freeOutputBuffers(): Unit = {
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }
      if (bufOut1 != null) {
        bufOut1.release()
        bufOut1 = null
      }
      if (bufOut2 != null) {
        bufOut2.release()
        bufOut2 = null
      }
      if (bufOut3 != null) {
        bufOut3.release()
        bufOut3 = null
      }
      if (bufOut4 != null) {
        bufOut4.release()
        bufOut4 = null
      }
    }

    def updateCanRead(): Unit = {
      val sh = shape
      _canRead = isAvailable(sh.in0) &&
        ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3))
    }

    private def startNextWindow(inOff: Int): Long = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        width = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        height = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        thresh = bufIn3.buf(inOff)
      }
      winSize = width * height
      if (winSize != oldSize) {
        updateSize()
      }
      winSize
    }

    private def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    private def processWindow(writeToWinOff: Long): Int = {
      val a     = winBuf
      val size  = winSize
      if (writeToWinOff < size) {
        val writeOffI = writeToWinOff.toInt
        Util.clear(a, writeOffI, size - writeOffI)
      }

      detectBlobs()
      blobs.length
    }

    @inline
    private[this] def canWriteToWindow = !readNumBlobsRemain && readBlobBoundsRemain == 0 && inValid

    private def processChunk(): Boolean = {
      var stateChange = false

      if (canWriteToWindow) {
        val flushIn0 = inputsEnded // inRemain == 0 && shouldComplete()
        if (isNextWindow && !flushIn0) {
          writeToWinRemain  = startNextWindow(inOff = inOff)
          isNextWindow      = false
          stateChange       = true
          // logStream(s"startNextWindow(); writeToWinRemain = $writeToWinRemain")
        }

        val chunk     = math.min(writeToWinRemain, inRemain).toInt
        val flushIn   = flushIn0 && writeToWinOff > 0
        if (chunk > 0 || flushIn) {
          // logStream(s"writeToWindow(); inOff = $inOff, writeToWinOff = $writeToWinOff, chunk = $chunk")
          if (chunk > 0) {
            copyInputToWindow(inOff = inOff, writeToWinOff = writeToWinOff, chunk = chunk)
            inOff            += chunk
            inRemain         -= chunk
            writeToWinOff    += chunk
            writeToWinRemain -= chunk
            stateChange       = true
          }

          if (writeToWinRemain == 0 || flushIn) {
            readBlobBoundsRemain  = processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
            outRemain1            = math.min(outRemain1, readBlobBoundsRemain)
            readNumBlobsRemain    = true
            writeToWinOff         = 0
            readBlobBoundsOff     = 0
            isNextWindow          = true
            stateChange           = true
            // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
          }
        }
      }

      if (readNumBlobsRemain && outRemain0 > 0) {
        bufOut0.buf(0) = blobs.length
        outOff0       += 1
        outRemain0    -= 1
      }

      if (readBlobBoundsRemain > 0 && outRemain1 > 0) {
        val chunk = math.min(readBlobBoundsRemain, outRemain1)
        readBlobBoundsOff    += chunk
        readBlobBoundsRemain -= chunk
        outOff1              += chunk
        outRemain1           -= chunk
        stateChange           = true
      }

      stateChange
    }

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    @tailrec
    def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (shouldRead) {
        inRemain    = readIns()
        inOff       = 0
        stateChange = true
      }

      if (outSent0) {
        bufOut0       = ctrl.borrowBufI()
        outRemain0    = 1
        outOff0       = 0
        outSent0      = false
        stateChange   = true
      }

      if (outSent1) {
        bufOut1       = ctrl.borrowBufI()
        bufOut2       = ctrl.borrowBufI()
        bufOut3       = ctrl.borrowBufI()
        bufOut4       = ctrl.borrowBufI()
        outRemain1    = bufOut1.size
        outOff1       = 0
        outSent1      = false
        stateChange   = true
      }

      if (inValid && processChunk()) stateChange = true

      val flushOut  = shouldComplete()
      val cw        = canWrite
      if (!outSent0 && (outRemain0 == 0 || flushOut) && cw) {
        writeOuts0()
        outSent0    = true
        stateChange = true
      }
      if (!outSent1 && (outRemain1 == 0 || flushOut) && cw) {
        writeOuts1()
        outSent1    = true
        stateChange = true
      }

      if (flushOut && outSent0 && outSent1) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }

    private def shouldComplete(): Boolean =
      inputsEnded && writeToWinOff == 0 && !readNumBlobsRemain && readBlobBoundsRemain == 0

    // ---- the fun part ----

    private[this] var gridVisited: Array[Boolean] = _

    private def updateSize(): Unit = {
      winBuf      = new Array[Double ](winSize)
      gridVisited = new Array[Boolean](winSize)
    }

    private def detectBlobs(): Unit = {
      val nbGridValue = winSize
      ???
    }
  }
}