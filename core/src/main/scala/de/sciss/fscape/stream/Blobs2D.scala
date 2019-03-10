/*
 *  Blobs2D.scala
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

import java.util

import akka.stream.stage.OutHandler
import akka.stream.{Attributes, Outlet}
import de.sciss.fscape.stream.impl.{AuxInHandlerImpl, FilterLogicImpl, FullInOutImpl, In5Out4Shape, NodeImpl, ProcessInHandlerImpl, StageImpl}

import scala.annotation.{switch, tailrec}

object Blobs2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh: OutD, pad: OutI)
           (implicit b: Builder): (OutI, OutD, OutI, OutD) = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(width , stage.in1)
    b.connect(height, stage.in2)
    b.connect(thresh, stage.in3)
    b.connect(pad   , stage.in4)
    (stage.out0, stage.out1, stage.out2, stage.out3)
  }

  private final val name = "Blobs2D"

  private type Shape = In5Out4Shape[BufD, BufI, BufI, BufD, BufI,   BufI, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = In5Out4Shape(
      in0  = InD (s"$name.in"         ),
      in1  = InI (s"$name.width"      ),
      in2  = InI (s"$name.height"     ),
      in3  = InD (s"$name.thresh"     ),
      in4  = InI (s"$name.pad"        ),
      out0 = OutI(s"$name.numBlobs"   ),
      out1 = OutD(s"$name.bounds"     ),
      out2 = OutI(s"$name.numVertices"),
      out3 = OutD(s"$name.vertices"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
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

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
    with FullInOutImpl[Shape]
    with FilterLogicImpl[BufD, Shape] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var widthIn   : Int           = _
    private[this] var heightIn  : Int           = _
    private[this] var widthPad  : Int           = _
    private[this] var heightPad : Int           = _
    private[this] var thresh    : Double        = _
    private[this] var pad       : Int           = _
    private[this] var winSizeIn : Int           = _
    private[this] var winSizePad: Int           = _

    private[this] var writeToWinOff             = 0
    private[this] var writeToWinRemain          = 0
    private[this] var readNumBlobsRemain        = 0
    private[this] var readBlobBoundsOff         = 0
    private[this] var readBlobBoundsRemain      = 0
    private[this] var readBlobNumVerticesOff    = 0
    private[this] var readBlobNumVerticesRemain = 0
    private[this] var readBlobVerticesRemain    = 0
    private[this] var isNextWindow              = true

    private[this] var readBlobVerticesBlobIdx   = 0   // sub-index during readBlobVerticesRemain > 0
    private[this] var readBlobVerticesVertexIdx = 0   // sub-index during readBlobVerticesRemain > 0

    protected     var bufIn0 : BufD  = _
    private[this] var bufIn1 : BufI  = _
    private[this] var bufIn2 : BufI  = _
    private[this] var bufIn3 : BufD  = _
    private[this] var bufIn4 : BufI  = _
    private[this] var bufOut0: BufI = _
    private[this] var bufOut1: BufD = _
    private[this] var bufOut2: BufI = _
    private[this] var bufOut3: BufD = _

    protected def in0: InD = shape.in0

    private[this] var _canRead  = false
    private[this] var _inValid  = false
    private[this] var _canWrite = false

    private[this] var inOff           = 0  // regarding `bufIn`
    protected     var inRemain        = 0
    private[this] var outOff0         = 0  // regarding `bufOut0`
    private[this] var outOff1         = 0  // regarding `bufOut1`
    private[this] var outOff2         = 0  // regarding `bufOut2`
    private[this] var outOff3         = 0  // regarding `bufOut3`
    private[this] var outRemain0      = 0
    private[this] var outRemain1      = 0
    private[this] var outRemain2      = 0
    private[this] var outRemain3      = 0

    private[this] var outSent0        = true
    private[this] var outSent1        = true
    private[this] var outSent2        = true
    private[this] var outSent3        = true

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
    new AuxInHandlerImpl     (shape.in4, this)
    new OutHandlerImpl       (shape.out0)
    new OutHandlerImpl       (shape.out1)
    new OutHandlerImpl       (shape.out2)
    new OutHandlerImpl       (shape.out3)

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
      if (outOff0 > 0 && isAvailable(shape.out0)) {
        bufOut0.size = outOff0
        push(shape.out0, bufOut0)
      } else {
        bufOut0.release()
      }
      bufOut0   = null
      _canWrite = false
    }

    private def writeOuts1(): Unit = {
      if (outOff1 > 0 && isAvailable(shape.out1)) {
        bufOut1.size = outOff1
        push(shape.out1, bufOut1)
      } else {
        bufOut1.release()
      }
      bufOut1 = null
      _canWrite = false
    }

    private def writeOuts2(): Unit = {
      if (outOff2 > 0 && isAvailable(shape.out2)) {
        bufOut2.size = outOff2
        push(shape.out2, bufOut2)
      } else {
        bufOut2.release()
      }
      bufOut2 = null
      _canWrite = false
    }

    private def writeOuts3(): Unit = {
      if (outOff3 > 0 && isAvailable(shape.out3)) {
        bufOut3.size = outOff3
        push(shape.out3, bufOut3)
      } else {
        bufOut3.release()
      }
      bufOut3 = null
      _canWrite = false
    }

    override protected def stopped(): Unit = {
      winBuf      = null
      blobs       = null
      gridVisited = null
      linesToDraw = null
//      voxels      = null
      edgeVrtX    = null
      edgeVrtY    = null

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
      if (isAvailable(sh.in4)) {
        bufIn4 = grab(sh.in4)
        tryPull(sh.in4)
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
      if (bufIn4 != null) {
        bufIn4.release()
        bufIn4 = null
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
    }

    def updateCanRead(): Unit = {
      val sh = shape
      _canRead = isAvailable(sh.in0) &&
        ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1)) &&
        ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2)) &&
        ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3)) &&
        ((isClosed(sh.in4) && _inValid) || isAvailable(sh.in4))
    }

    private def startNextWindow(inOff: Int): Int = {
      val oldSizeIn   = winSizeIn
      val oldSizePad  = winSizePad
      val oldThresh   = thresh

      if (bufIn1 != null && inOff < bufIn1.size) {
        widthIn = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        heightIn = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        thresh = bufIn3.buf(inOff)
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        pad = math.max(0, bufIn4.buf(inOff))
      }
      val pad2        = pad * 2
      val _widthIn    = widthIn
      val _heightIn   = heightIn

      val _widthPad   = _widthIn  + pad2
      val _heightPad  = _heightIn + pad2
      val _winSizeIn  = _widthIn  * _heightIn
      val _winSizePad = _widthPad * _heightPad

      widthPad        = _widthPad
      heightPad       = _heightPad
      winSizeIn       = _winSizeIn
      winSizePad      = _winSizePad

      if (_winSizePad != oldSizePad || _winSizeIn != oldSizeIn) {
        updateSize()
        if (thresh != 0.0) fillThresh()
      } else if (thresh != oldThresh) {
        fillThresh()
      }

      _winSizeIn
    }

    private def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = {
      val _pad    = pad
      val _bufIn  = bufIn0.buf
      val _bufOut = winBuf

      if (_pad == 0) {
        Util.copy(_bufIn, inOff, _bufOut, writeToWinOff, chunk)

      } else {
        var remain    = chunk
        var _inOff    = inOff
        val _width    = widthIn
        val _widthPad = widthPad
        var x         = writeToWinOff % _width
        val y         = writeToWinOff / _width
        var _outOff   = (y + _pad) * _widthPad + _pad   // beginning of "inner line"
        while (remain > 0) {
          val num = math.min(_width - x, remain)
          Util.copy(_bufIn, _inOff, _bufOut, _outOff + x, num)
          _inOff  += num
          remain  -= num
          x        = 0
          _outOff += _widthPad
        }
      }
    }

    private def processWindow(writeToWinOff: Int): Unit = {
//      val a     = winBuf
//      val size  = winSizeIn
//      val _pad = pad
//      if (_pad == 0) {
//        if (writeToWinOff < size) {
//          Util.fill(a, writeToWinOff, size - writeToWinOff, thresh)
//        }
//      } else {
//        ...
//      }

      detectBlobs()
      val _numBlobs             = numBlobs
      readNumBlobsRemain        = 1
      readBlobBoundsRemain      = _numBlobs * 4
      readBlobNumVerticesRemain = _numBlobs

      readBlobVerticesBlobIdx   = 0
      readBlobVerticesVertexIdx = 0
      var vertexCount = 0
      var i = 0
      while (i < _numBlobs) {
        val b = blobs(i)
        vertexCount += b.numLines * 2
        i += 1
      }

      readBlobVerticesRemain    = vertexCount
      outRemain1                = math.min(outRemain1, readBlobBoundsRemain)
      outRemain2                = math.min(outRemain2, readBlobNumVerticesRemain)
      outRemain3                = math.min(outRemain3, readBlobVerticesRemain)
      readBlobBoundsOff         = 0
      readBlobNumVerticesOff    = 0
    }

    @inline
    private[this] def canWriteToWindow =
      inValid &&
        readNumBlobsRemain        == 0 &&
        readBlobBoundsRemain      == 0 &&
        readBlobNumVerticesRemain == 0 &&
        readBlobVerticesRemain    == 0

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

        val chunk     = math.min(writeToWinRemain, inRemain) // .toInt
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
            processWindow(writeToWinOff = writeToWinOff) // , flush = flushIn)
            writeToWinOff         = 0
            isNextWindow          = true
            stateChange           = true
            // logStream(s"processWindow(); readFromWinRemain = $readFromWinRemain")
          }
        }
      }

      // ---- num-blobs ----
      if (readNumBlobsRemain > 0 && outRemain0 > 0) {
        bufOut0.buf(0)      = numBlobs
        readNumBlobsRemain -= 1
        outOff0            += 1
        outRemain0         -= 1
      }

      // ---- blob bounds ----
      if (readBlobBoundsRemain > 0 && outRemain1 > 0) {
        val chunk     = math.min(readBlobBoundsRemain, outRemain1)
        var _offIn    = readBlobBoundsOff
        var _offOut   = outOff1
        val _blobs    = blobs
        val _buf      = bufOut1.buf
        val stop      = _offOut + chunk
        val _pad      = pad
        while (_offOut < stop) {
          val blobIdx = _offIn / 4
          val blob    = _blobs(blobIdx)
          val coord   = (_offIn % 4: @switch) match {
            case 0 => blob.xMin
            case 1 => blob.xMax
            case 2 => blob.yMin
            case 3 => blob.yMax
          }
          _buf(_offOut) = coord - _pad
          _offIn  += 1
          _offOut += 1
        }
        
        readBlobBoundsOff     = _offIn
        readBlobBoundsRemain -= chunk
        outOff1               = _offOut
        outRemain1           -= chunk
        stateChange           = true
      }

      // ---- blob num-vertices ----
      if (readBlobNumVerticesRemain > 0 && outRemain2 > 0) {
        val chunk     = math.min(readBlobNumVerticesRemain, outRemain2)
        var _offIn    = readBlobNumVerticesOff
        var _offOut   = outOff2
        val _blobs    = blobs
        val _buf      = bufOut2.buf
        val stop      = _offOut + chunk
        while (_offOut < stop) {
          val blob      = _blobs(_offIn)
          _buf(_offOut) = blob.numLines
          _offIn  += 1
          _offOut += 1
        }

        readBlobNumVerticesOff     = _offIn
        readBlobNumVerticesRemain -= chunk
        outOff2                    = _offOut
        outRemain2                -= chunk
        stateChange                = true
      }

      // ---- blob vertices ----
      if (readBlobVerticesRemain > 0 && outRemain3 > 0) {
        val chunk     = math.min(readBlobVerticesRemain, outRemain3)
        var _offOut   = outOff3
        val _blobs    = blobs
        val _buf      = bufOut3.buf
        val stop      = _offOut + chunk
        var _blobIdx  = readBlobVerticesBlobIdx
        var _vIdx     = readBlobVerticesVertexIdx
        val _edgeX    = edgeVrtX
        val _edgeY    = edgeVrtY
        val _pad      = pad
        while (_offOut < stop) {
          val blob      = _blobs(_blobIdx)
          val vCount    = blob.numLines * 2
          val chunk2    = math.min(stop - _offOut, vCount - _vIdx)
          if (chunk2 > 0) {
            val stop2 = _vIdx + chunk2
            while (_vIdx < stop2) {
              val lineIdx = blob.line(_vIdx & ~1)
              val table   = if (_vIdx % 2 == 0) _edgeX else _edgeY
              val value   = table(lineIdx)
              _buf(_offOut) = value - _pad
              _vIdx   += 1
              _offOut += 1
            }
          } else {
            _blobIdx += 1
            _vIdx     = 0
          }
        }

        readBlobVerticesBlobIdx   = _blobIdx
        readBlobVerticesVertexIdx = _vIdx
        readBlobVerticesRemain   -= chunk
        outOff3                   = _offOut
        outRemain3               -= chunk
        stateChange               = true
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
        bufOut1       = ctrl.borrowBufD()
        outRemain1    = bufOut1.size
        outOff1       = 0
        outSent1      = false
        stateChange   = true
      }
      
      if (outSent2) {
        bufOut2       = ctrl.borrowBufI()
        outRemain2    = bufOut2.size
        outOff2       = 0
        outSent2      = false
        stateChange   = true
      }

      if (outSent3) {
        bufOut3       = ctrl.borrowBufD()
        outRemain3    = bufOut3.size
        outOff3       = 0
        outSent3      = false
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
      if (!outSent2 && (outRemain2 == 0 || flushOut) && cw) {
        writeOuts2()
        outSent2    = true
        stateChange = true
      }
      if (!outSent3 && (outRemain3 == 0 || flushOut) && cw) {
        writeOuts3()
        outSent3    = true
        stateChange = true
      }

      if (flushOut && outSent0 && outSent1 && outSent2) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }

    private def shouldComplete(): Boolean =
      inputsEnded && writeToWinOff == 0 &&
        readNumBlobsRemain         == 0 &&
        readBlobBoundsRemain       == 0 &&
        readBlobNumVerticesRemain  == 0 &&
        readBlobVerticesRemain     == 0

    // ---- the fun part ----
    // this is an adaptation of Gachadoat's algorithm

    private[this] var gridVisited: Array[Boolean] = _
    private[this] var linesToDraw: Array[Int]     = _
    private[this] var edgeVrtX   : Array[Double]  = _
    private[this] var edgeVrtY   : Array[Double]  = _
    private[this] var numLinesToDraw  = 0
    private[this] var numBlobs        = 0

    private def updateSize(): Unit = {
      val _winSize  = winSizePad
      val winSz2    = _winSize * 2
      winBuf        = new Array[Double ](_winSize)
      gridVisited   = new Array[Boolean](_winSize)
      linesToDraw   = new Array[Int    ]( winSz2 )
      edgeVrtX      = new Array[Double ]( winSz2 )
      edgeVrtY      = new Array[Double ]( winSz2 )
      blobs         = Array.fill(MaxNumBlobs)(new Blob)

      // Note: using stepX in the coordinate calculation
      // means that xMin, xMax, yMin, yMax are in the range
      // of 0 to 1; when scaling back to image coordinates,
      // one would have to multiply by width-1 and height-1,
      // respectively.

//      stepX         = 1.0 / math.max(1, width  - 1)
//      stepY         = 1.0 / math.max(1, height - 1)
    }

    private def fillThresh(): Unit =
      Util.fill(winBuf, 0, winSizePad, thresh)

    private def detectBlobs(): Unit = {
      val _visited  = gridVisited
      util.Arrays.fill(_visited, false)
      numLinesToDraw = 0

      var _numBlobs   = 0
      val _blobs      = blobs
//      val _pad        = pad
//      val _pad2       = _pad * 2
      val _width      = widthPad
      val _widthM     = _width - 1
      val _height     = heightPad
      val _heightM    = _height - 1

      // reset edge coordinates
      val _edgeX  = edgeVrtX
      val _edgeY  = edgeVrtY
      var x = 0
      var y = 0
      var n = 0
      while (x < _width) {
        y = 0
        while (y < _height) {
          _edgeX(n) = x
          _edgeY(n) = y
          n += 1
          _edgeX(n) = x
          _edgeY(n) = y
          n += 1
          y += 1
        }
        x += 1
      }

      x = 0
      while (x < _widthM) {
        y = 0
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

      val valid = blob.xMin < /* <= */ blob.xMax && blob.yMin < /* <= */ blob.yMax
      if (valid) {
        blob.numLines /= 2    // XXX TODO --- why is this? looks wrong
      }
      valid
    }

    private def computeEdgeVertex(blob: Blob, x: Int, y: Int): Unit = {
      val _width  = widthPad
      val _height = heightPad
      val offset  = x + _width * y

      @inline
      def voxel(x: Int, y: Int): Int = (y + (x * _height)) * 2

      val _visited = gridVisited
      if (_visited(offset)) return
      _visited(offset) = true

      val sqrIdx  = getSquareIndex(x, y)
      var n       = 0
      var iEdge   = edgeCuts(sqrIdx)(n)
      while (iEdge != -1) {
        if (blob.numLines == MaxNumLines) return

        val edgeOffInf  = edgeOffsetInfo(iEdge)
        val offX        = edgeOffInf(0)
        val offY        = edgeOffInf(1)
        val offAB       = edgeOffInf(2)
        val v           = voxel(x + offX, y + offY) + offAB

        linesToDraw(numLinesToDraw) = v
        numLinesToDraw += 1

        blob.line(blob.numLines) = v
        blob.numLines += 1

        n += 1
        iEdge = edgeCuts(sqrIdx)(n)
      }

      val toCompute = edgesToCompute(sqrIdx)

      if (toCompute > 0) {
        if ((toCompute & 1) > 0) { // Edge 0
          val _buf    = winBuf
          val t       = (thresh - _buf(offset)) / (_buf(offset + 1) - _buf(offset))
          val value   = x * (1.0 - t) + t * (x + 1)
          val edgeIdx = voxel(x, y)
          edgeVrtX(edgeIdx)                 = value
          if (value < blob.xMin) blob.xMin  = value
          if (value > blob.xMax) blob.xMax  = value
        }
        if ((toCompute & 2) > 0) { // Edge 3
          val _buf    = winBuf
          val t       = (thresh - _buf(offset)) / (_buf(offset + _width) - _buf(offset))
          val value   = y * (1.0 - t) + t * (y + 1)
          val edgeIdx = voxel(x, y) + 1
          edgeVrtY(edgeIdx)                 = value
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
      val _width  = widthPad
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