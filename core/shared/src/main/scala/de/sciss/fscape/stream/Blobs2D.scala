/*
 *  Blobs2D.scala
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

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain, OutIMain}
import de.sciss.fscape.stream.impl.shapes.In5Out4Shape
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.{switch, tailrec}
import scala.math.{max, min}

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

  private type Shp = In5Out4Shape[BufD, BufI, BufI, BufD, BufI,   BufI, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = In5Out4Shape(
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

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
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

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hIn           : InDMain   = InDMain (this, shape.in0)
    private[this] val hWidth        : InIAux    = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hHeight       : InIAux    = InIAux  (this, shape.in2)(max(1, _))
    private[this] val hThresh       : InDAux    = InDAux  (this, shape.in3)()
    private[this] val hPad          : InIAux    = InIAux  (this, shape.in4)(max(0, _))
    private[this] val hOutNumBlobs  : OutIMain  = OutIMain(this, shape.out0)
    private[this] val hOutBounds    : OutDMain  = OutDMain(this, shape.out1)
    private[this] val hOutNumVert   : OutIMain  = OutIMain(this, shape.out2)
    private[this] val hOutVertices  : OutDMain  = OutDMain(this, shape.out3)

    private[this] var winBuf    : Array[Double] = _
    private[this] var widthIn   : Int           = _
    private[this] var heightIn  : Int           = _
    private[this] var widthPad  : Int           = _
    private[this] var heightPad : Int           = _
    private[this] var thresh    : Double        = _
    private[this] var pad       : Int           = _
    private[this] var winSizeIn : Int           = _
    private[this] var winSizePad: Int           = _

    private[this] var readOff: Int = _
    private[this] var readRem: Int = _
    
    private[this] var stage = 0

    private[this] var writeOffNumBlobs    : Int = _
    private[this] var writeOffBounds      : Int = _
    private[this] var writeOffNumVertices : Int = _
    private[this] var writeOffVertices    : Int = _

    private[this] var writeRem            : Int = 0 // sum of the below, for quick termination check
    private[this] var writeRemNumBlobs    : Int = _
    private[this] var writeRemBounds      : Int = _
    private[this] var writeRemNumVertices : Int = _
    private[this] var writeRemVertices    : Int = _

    private[this] var writeBlobVerticesBlobIdx   = 0   // sub-index during writeRemVertices > 0
    private[this] var writeBlobVerticesVertexIdx = 0   // sub-index during writeRemVertices > 0

    // result of blob analysis
    private[this] var blobs: Array[Blob] = _

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf      = null
      blobs       = null
      gridVisited = null
      linesToDraw = null
//      voxels      = null
      edgeVrtX    = null
      edgeVrtY    = null
    }

    private def tryObtainWinParams(): Boolean = {
      val ok =
        hWidth  .hasNext &&
        hHeight .hasNext &&
        hThresh .hasNext &&
        hPad    .hasNext

      if (ok) {
        val oldSizeIn   = winSizeIn
        val oldSizePad  = winSizePad
        val oldThresh   = thresh

        widthIn         = hWidth  .next()
        heightIn        = hHeight .next()
        thresh          = hThresh .next()
        pad             = hPad    .next()

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
      }
      ok
    }

    private def winBufSize : Int = winSizeIn
    private def readWinSize: Int = winSizeIn

    private def clearWindowTail(): Unit = {
      val chunk         = winSizeIn - readOff
      val _pad          = pad
      val _bufOut       = winBuf
      val writeToWinOff = readOff // .toInt
      val _thresh       = thresh

      if (_pad == 0) {
        Util.fill(_bufOut, writeToWinOff, chunk, _thresh)

      } else {
        var remain    = chunk
        val _width    = widthIn
        val _widthPad = widthPad
        var x         = writeToWinOff % _width
        val y         = writeToWinOff / _width
        var _outOff   = (y + _pad) * _widthPad + _pad   // beginning of "inner line"
        while (remain > 0) {
          val num = math.min(_width - x, remain)
          Util.fill(_bufOut, _outOff + x, num, _thresh)
          remain  -= num
          x        = 0
          _outOff += _widthPad
        }
      }
    }

    private def readIntoWindow(chunk: Int): Unit = {
      val _pad          = pad
      val _bufIn        = hIn.array
      val inOff         = hIn.offset
      val _bufOut       = winBuf
      val writeToWinOff = readOff // .toInt

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

      hIn.advance(chunk)
    }

    private def processWindow(): Unit = {
      if (readOff < winSizeIn) clearWindowTail()

      detectBlobs()
      val _numBlobs = numBlobs

      var vertexCount = 0
      var i = 0
      while (i < _numBlobs) {
        val b = blobs(i)
        vertexCount += b.numLines * 2
        i += 1
      }

      writeRemNumBlobs    = if (hOutNumBlobs.isDone) 0 else 1
      writeRemBounds      = if (hOutBounds  .isDone) 0 else _numBlobs * 4
      writeRemNumVertices = if (hOutNumVert .isDone) 0 else _numBlobs
      writeRemVertices    = if (hOutVertices.isDone) 0 else vertexCount
      writeRem            = writeRemNumBlobs + writeRemBounds + writeRemNumVertices + writeRemVertices
    }

    private def processOutput(): Unit = {
      // ---- num-blobs ----
      if (writeRemNumBlobs > 0 && hOutNumBlobs.hasNext) {
        assert (writeRemNumBlobs == 1)
        hOutNumBlobs.next(numBlobs)
        writeRemNumBlobs -= 1
        writeRem         -= 1
      }

      // ---- blob bounds ----
      if (writeRemBounds > 0 && hOutBounds.hasNext) {
        val chunk     = min(writeRemBounds, hOutBounds.available)
        var _offIn    = writeOffBounds
        val out       = hOutBounds
        val _blobs    = blobs
        val stop      = _offIn + chunk
        val _pad      = pad
        while (_offIn < stop) {
          val blobIdx = _offIn / 4
          val blob    = _blobs(blobIdx)
          val coord   = (_offIn % 4: @switch) match {
            case 0 => blob.xMin
            case 1 => blob.xMax
            case 2 => blob.yMin
            case 3 => blob.yMax
          }
          out.next(coord - _pad)
          _offIn  += 1
        }
        
        writeRemBounds -= chunk
        writeRem       -= chunk
        writeOffBounds += chunk
      }

      // ---- blob num-vertices ----
      if (writeRemNumVertices > 0 && hOutNumVert.hasNext) {
        val chunk     = min(writeRemNumVertices, hOutNumVert.available)
        var _offIn    = writeOffNumVertices
        val _blobs    = blobs
        val out       = hOutNumVert
        val stop      = _offIn + chunk
        while (_offIn < stop) {
          val blob = _blobs(_offIn)
          out.next(blob.numLines)
          _offIn  += 1
        }

        writeRemNumVertices -= chunk
        writeRem            -= chunk
        writeOffNumVertices += chunk
      }

      // ---- blob vertices ----
      if (writeRemVertices > 0 && hOutVertices.hasNext) {
        val chunk     = min(writeRemVertices, hOutVertices.available)
        val _blobs    = blobs
        val out       = hOutVertices
        var _blobIdx  = writeBlobVerticesBlobIdx
        var _vIdx     = writeBlobVerticesVertexIdx
        val _edgeX    = edgeVrtX
        val _edgeY    = edgeVrtY
        val _pad      = pad
        var i = 0
        while (i < chunk) {
          val blob      = _blobs(_blobIdx)
          val vCount    = blob.numLines * 2
          val chunk2    = math.min(chunk - i, vCount - _vIdx)
          if (chunk2 > 0) {
            val stop2 = _vIdx + chunk2
            while (_vIdx < stop2) {
              val lineIdx = blob.line(_vIdx & ~1)
              val table   = if (_vIdx % 2 == 0) _edgeX else _edgeY
              val value   = table(lineIdx)
              out.next(value - _pad)
              i += 1
              _vIdx   += 1
            }
          } else {
            _blobIdx += 1
            _vIdx     = 0
          }
        }

        writeBlobVerticesBlobIdx   = _blobIdx
        writeBlobVerticesVertexIdx = _vIdx
        writeRemVertices          -= chunk
        writeRem                  -= chunk
      }
    }

    private def prepareStage2(): Unit = {
      processWindow()
      writeOffNumBlobs            = 0
      writeOffBounds              = 0
      writeOffNumVertices         = 0
      writeOffVertices            = 0
      writeBlobVerticesBlobIdx    = 0
      writeBlobVerticesVertexIdx  = 0
    }

    @tailrec
    protected def process(): Unit = {
      logStream(s"process() $this")

      if (stage == 0) {
        if (!tryObtainWinParams()) return

        val _winBufSz = winBufSize
        if (winBuf == null || winBuf.length != _winBufSz) {
          winBuf = if (_winBufSz == 0) null else new Array(_winBufSz)
        }

        readOff   = 0
        readRem   = readWinSize
        stage     = 1
      }

      while (stage == 1) {
        val remIn = hIn.available
        if (remIn == 0) return
        val numIn = min(remIn, readRem)
        if (numIn > 0) readIntoWindow(numIn)
        readOff += numIn
        readRem -= numIn
        if (hIn.isDone || readRem == 0) {
          prepareStage2()
          stage = 2
        }
      }

      while (stage == 2) {
        val oldRem = writeRem
        if (writeRem > 0) processOutput()
        if (writeRem == 0) {
          if (hIn.isDone) {
            if (flushOut()) completeStage()
            return
          } else {
            stage = 0
          }
        } else if (writeRem == oldRem) return
      }

      process()
    }

    override protected def onDone(outlet: Outlet[_]): Unit = {
      if (stage == 2) {
        // val oldRem = writeRem
        if (outlet == hOutVertices.outlet) {
          writeRem -= writeRemVertices
          writeRemVertices = 0
        } else if (outlet == hOutBounds.outlet) {
          writeRem -= writeRemBounds
          writeRemBounds = 0
        } else if (outlet == hOutNumVert.outlet) {
          writeRem -= writeRemNumVertices
          writeRemNumVertices = 0
        } else {
          assert (outlet == hOutNumBlobs.outlet)
          writeRem -= writeRemNumBlobs
          writeRemNumBlobs = 0
        }
        // always call `process` in order to ensure `completeStage` may be invoked.
        process()

      } else {
        if (hOutNumBlobs.isDone && hOutBounds.isDone && hOutNumVert.isDone && hOutVertices.isDone) {
          // writeRem  = 0
          // stage     = 2
          if (flushOut()) completeStage()
        }
      }
    }

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == hIn.inlet)
      if (stage == 0 || (stage == 1 && readOff == 0L)) {
        stage = 2
        if (flushOut()) completeStage()
      } else if (stage == 1) { // i.e. readOff > 0
        prepareStage2()
        stage = 2
        process()
      }
    }

    private def flushOut(): Boolean =
      hOutNumBlobs.flush() &
      hOutBounds  .flush() &
      hOutNumVert .flush() &
      hOutVertices.flush()

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