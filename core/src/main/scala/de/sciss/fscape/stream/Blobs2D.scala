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

import java.util

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

  private final class Blob {
    var xMin      = 0.0
    var xMax      = 0.0
    var yMin      = 0.0
    var yMax      = 0.0
    var numLines  = 0
    val line      = new Array[Int](MaxNumLines) // stack of index

    override def toString = f"Blob(xMin = $xMin%g, xMax = $xMax%g, yMin = $yMin%g, yMax = $yMax%g, numLines = $numLines)"

//    def reset(): Unit = {
//      xMin      = 0.0
//      xMax      = 0.0
//      yMin      = 0.0
//      yMax      = 0.0
//      numLines  = 0
//    }
  }

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

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
      pull(sh.in2)
      pull(sh.in3)
    }

    override protected def stopped(): Unit = {
      winBuf      = null
      blobs       = null
      gridVisited = null
      linesToDraw = null
      voxels      = null
//      edgeVrtX    = null
//      edgeVrtY    = null

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
      numBlobs
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
        bufOut0.buf(0) = numBlobs
        outOff0       += 1
        outRemain0    -= 1
      }

      if (readBlobBoundsRemain > 0 && outRemain1 > 0) {
        val chunk     = math.min(readBlobBoundsRemain, outRemain1)
        var _offIn    = readBlobBoundsOff
        var _offOut   = outOff1
        val _blobs    = blobs
        val _bufMinX  = bufOut1.buf
        val _bufMaxX  = bufOut2.buf
        val _bufMinY  = bufOut3.buf
        val _bufMaxY  = bufOut4.buf
        val _width    = width
        val _height   = height
        val stop      = _offOut + chunk
        while (_offOut < stop) {
          val blob  = _blobs(_offIn)
          _bufMinX(_offOut) = math.floor(blob.xMin * width ).toInt
          _bufMaxX(_offOut) = math.ceil (blob.xMax * width ).toInt
          _bufMinY(_offOut) = math.floor(blob.yMin * height).toInt
          _bufMaxY(_offOut) = math.ceil (blob.yMax * height).toInt
          _offIn  += 1
          _offOut += 1
        }
        
        readBlobBoundsOff     = _offIn
        outOff1               = _offOut
        readBlobBoundsRemain -= chunk
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
    // this is basically a Scala translation of Gachadoat's algorithm

    private[this] var gridVisited: Array[Boolean] = _
    private[this] var stepX      : Double         = _
    private[this] var stepY      : Double         = _
    private[this] var linesToDraw: Array[Int]     = _
    private[this] var voxels     : Array[Int]     = _
//    private[this] var edgeVrtX   : Array[Double]  = _
//    private[this] var edgeVrtY   : Array[Double]  = _
    private[this] var numLinesToDraw  = 0
    private[this] var numBlobs        = 0

    private def updateSize(): Unit = {
      val _winSize  = winSize
      val winSz2    = _winSize * 2
      winBuf        = new Array[Double ](_winSize)
      gridVisited   = new Array[Boolean](_winSize)
      linesToDraw   = new Array[Int    ]( winSz2 )
      voxels        = new Array[Int    ](_winSize)
//      edgeVrtX      = new Array[Double ]( winSz2 )
//      edgeVrtY      = new Array[Double ]( winSz2 )
      blobs         = Array.fill(MaxNumBlobs)(new Blob)
      stepX         = 1.0 / math.max(1, width  - 1)
      stepY         = 1.0 / math.max(1, height - 1)

      // XXX TODO -- fill voxels, edgeVrtX, edgeVrtY
    }

    private def detectBlobs(): Unit = {
      val _visited  = gridVisited
      util.Arrays.fill(_visited, false)
      numLinesToDraw  = 0

      var _numBlobs = 0
      val _blobs    = blobs
      val _width    = width
      val _widthM   = _width - 1
      val _height   = height
      val _heightM  = _height - 1

      var x = 0
      while (x < _widthM) {
        var y = 0
        while (y < _heightM) {
          // > offset in the grid
          val offset = x + _width * y
          // > if we were already there, just go the next square!
          if (!_visited(offset)) {
            val sqrIdx = getSquareIndex(x, y)
            if (sqrIdx > 0 && sqrIdx < 15) {
              if (_numBlobs < MaxNumBlobs) {
                val blob  = _blobs(_numBlobs)
                val valid = findBlob(blob, x, y)
                if (valid) _numBlobs += 1
              }
            }
          }
          y += 1
        }
        x += 1
      }
      numLinesToDraw /= 2
      numBlobs        = _numBlobs
    }

    private def findBlob(blob: Blob, x: Int, y: Int): Boolean = {
      // Reset Blob values
      blob.xMin     = Double.PositiveInfinity
      blob.xMax     = Double.NegativeInfinity
      blob.yMin     = Double.PositiveInfinity
      blob.yMax     = Double.NegativeInfinity
      blob.numLines = 0

      computeEdgeVertex(blob, x, y)

      val valid = blob.xMin <= blob.xMax && blob.yMin <= blob.yMax
      if (valid) {
        blob.numLines /= 2    // XXX TODO --- why is this? looks wrong
      }
      valid
    }

    private def computeEdgeVertex(blob: Blob, x: Int, y: Int): Unit = {
      val _width  = width
      val _height = height
      val offset  = x + _width * y

      val _visited = gridVisited
      if (_visited(offset)) return
      _visited(offset) = true

      val sqrIdx  = getSquareIndex(x, y)
      var n       = 0
      var iEdge   = edgeCuts(sqrIdx)(n)
      while (iEdge != -1 /* && blob.numLines < MaxNumLines */) {
        if (blob.numLines == MaxNumLines) return

        val edgeOffInf  = edgeOffsetInfo(iEdge)
        val offX        = edgeOffInf(0)
        val offY        = edgeOffInf(1)
        val offAB       = edgeOffInf(2)
        val v           = voxels((x + offX) + _width * (y + offY)) + offAB

        linesToDraw(numLinesToDraw) = v
        numLinesToDraw += 1

        blob.line(blob.numLines) = v
        blob.numLines    += 1

        n    += 1
        iEdge = edgeCuts(sqrIdx)(n)
      }

      val toCompute = edgesToCompute(sqrIdx)

      if (toCompute > 0) {
        if ((toCompute & 1) > 0) { // Edge 0
          val _buf    = winBuf
          val t       = (thresh - _buf(offset)) / (_buf(offset + 1) - _buf(offset))
          val _stepX  = stepX
          val vx      = x * _stepX
          val value   = vx * (1.0 - t) + t * (vx + _stepX)
//          edgeVrtX(voxels(offset))          = value
          if (value < blob.xMin) blob.xMin  = value
          if (value > blob.xMax) blob.xMax  = value
        }
        if ((toCompute & 2) > 0) { // Edge 3
          val _buf    = winBuf
          val t       = (thresh - _buf(offset)) / (_buf(offset + width) - _buf(offset))
          val _stepY  = stepY
          val vy      = y * _stepY
          val value   = vy * (1.0 - t) + t * (vy + _stepY)
//          edgeVrtY(voxels(offset) + 1)      = value
          if (value < blob.yMin) blob.yMin  = value
          if (value > blob.yMax) blob.yMax  = value
        }
      }

      // Propagate to neighbors
      val neighVox = neighborVoxels(sqrIdx)
      if (x < _width  - 2 && (neighVox & (1 << 0)) > 0) computeEdgeVertex(blob, x + 1, y    )
      if (x > 0           && (neighVox & (1 << 1)) > 0) computeEdgeVertex(blob, x - 1, y    )
      if (y < _height - 2 && (neighVox & (1 << 2)) > 0) computeEdgeVertex(blob, x    , y + 1)
      if (y > 0           && (neighVox & (1 << 3)) > 0) computeEdgeVertex(blob, x    , y - 1)
    }

    private def getSquareIndex(x: Int, y: Int): Int = {
      val _width  = width
      val _buf    = winBuf
      val _thresh = thresh

      val offY    = _width * y
      val offY1   = offY + _width
      val x1      = x + 1

      var res = 0
      if (_buf(x  + offY ) > _thresh) res |= 1
      if (_buf(x1 + offY ) > _thresh) res |= 2
      if (_buf(x1 + offY1) > _thresh) res |= 4
      if (_buf(x  + offY1) > _thresh) res |= 8

      res
    }
  }

  private final val edgeCuts = Array[Array[Int]](
    Array(-1, -1, -1, -1, -1), //0
    Array( 0,  3, -1, -1, -1), //3
    Array( 0,  1, -1, -1, -1), //1
    Array( 3,  1, -1, -1, -1), //2
    Array( 1,  2, -1, -1, -1), //0
    Array( 1,  2,  0,  3, -1), //3
    Array( 0,  2, -1, -1, -1), //1
    Array( 3,  2, -1, -1, -1), //2
    Array( 3,  2, -1, -1, -1), //2
    Array( 0,  2, -1, -1, -1), //1
    Array( 1,  2,  0,  3, -1), //3
    Array( 1,  2, -1, -1, -1), //0
    Array( 3,  1, -1, -1, -1), //2
    Array( 0,  1, -1, -1, -1), //1
    Array( 0,  3, -1, -1, -1), //3
    Array(-1, -1, -1, -1, -1)  //0
  )

  private final val edgeOffsetInfo = Array[Array[Int]](
    Array(0, 0, 0), Array(1, 0, 1), Array(0, 1, 0), Array(0, 0, 1))

  private final val edgesToCompute = Array[Int](0, 3, 1, 2, 0, 3, 1, 2, 2, 1, 3, 0, 2, 1, 3, 0)
  private final val neighborVoxels = Array[Int](0, 10, 9, 3, 5, 15, 12, 6, 6, 12, 12, 5, 3, 9, 10, 0)

  // XXX TODO --- should be customizable
  private final val MaxNumLines   = 4000

  // XXX TODO --- should be customizable
  private final val MaxNumBlobs   = 1000
}