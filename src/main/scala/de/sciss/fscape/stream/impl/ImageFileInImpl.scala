/*
 *  ImageFileInImpl.scala
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
package impl

import java.awt.image.{BufferedImage, DataBuffer}
import javax.imageio.ImageIO

import akka.stream.Shape
import akka.stream.stage.OutHandler
import de.sciss.file.File

import scala.collection.immutable.{IndexedSeq => Vec}

/** Common building block for `ImageFileIn` and `ImageFileSeqIn` */
trait ImageFileInImpl[S <: Shape] extends OutHandler {
  _: StageLogicImpl[S] =>

  // ---- abstract ----

  protected def numChannels : Int
  protected def outBufs     : Array[BufD]
  protected def outlets     : Vec[OutD]

  protected def process(): Unit

  // ---- impl ----

  private[this]   var numBands    : Int             = _
  protected final var numFrames   : Int             = _
  protected final var framesRead  : Int             = _
  private[this]   var gain        : Double          = _
  private[this]   var pixBuf      : Array[Double]   = _
  protected final var img         : BufferedImage   = _

  /** Resets `framesRead`. */
  protected final def openImage(f: File): Unit = {
    closeImage()
//    println(s"openImage($f)")
    img         = ImageIO.read(f)
    numBands    = img.getSampleModel.getNumBands
    if (numBands != numChannels) {
      Console.err.println(s"Warning: ImageFileIn - $f - channel mismatch (file has $numBands, UGen has $numChannels)")
    }
    numFrames   = img.getWidth * img.getHeight
    val bufSize = numBands * img.getWidth
    pixBuf      = new Array(bufSize)

    val gainR = img.getSampleModel.getDataType match {
      case DataBuffer.TYPE_BYTE   =>   255.0
      case DataBuffer.TYPE_USHORT => 65535.0
      case DataBuffer.TYPE_FLOAT  =>     1.0
    }
    gain = 1.0 / gainR

    framesRead  = 0
  }

  protected final def closeImage(): Unit = {
    pixBuf = null
    if (img != null) {
      img.flush()
      img = null
    }
  }

  protected final def freeOutputBuffers(): Unit = {
    var i = 0
    while (i < outBufs.length) {
      if (outBufs(i) != null) {
        outBufs(i).release()
        outBufs(i) = null
      }
      i += 1
    }
  }

  private[this] def read(x: Int, y: Int, width: Int, height: Int, offIn: Int): Int = {
    val sz      = width * height
    val offOut  = offIn + sz
    img.getRaster.getPixels(x, y, width, height, pixBuf)
    var ch  = 0
    val a   = pixBuf
    val nb  = numBands
    val g   = gain
    while (ch < numChannels) {
      val out = outlets(ch)
      if (!isClosed(out)) {
        if (outBufs(ch) == null) outBufs(ch) = ctrl.borrowBufD()
        val bufOut  = outBufs(ch)
        val b       = bufOut.buf
        if (ch < nb) {
          var i = ch
          var j = offIn
          while (j < offOut) {
            b(j) = a(i) * g
            i   += nb
            j   += 1
          }
        } else {
          Util.clear(b, offIn, sz)
        }
      }
      ch += 1
    }
    offOut
  }

  protected final def processChunk(outOff: Int, chunk: Int): Unit = {
    val stop  = framesRead + chunk
    val w     = img.getWidth
    val x0    = framesRead % w
    val y0    = framesRead / w
    val x1    = stop       % w
    val y1    = stop       / w

    // first (partial) line
    val off0 = read(
      x       = x0,
      y       = y0,
      width   = (if (y1 == y0) x1 else w) - x0,
      height  = 1,
      offIn   = outOff
    )

    // middle lines
    val hMid  = y1 - y0 - 2
    val off1 = if (hMid <= 0) off0 else read(
      x       = 0,
      y       = y0 + 1,
      width   = w,
      height  = hMid,
      offIn   = off0
    )

    // last (partial) line
    if (y1 > y0 && x1 > 0) read(
      x       = 0,
      y       = y1,
      width   = x1,
      height  = 1,
      offIn   = off1
    )

    framesRead += chunk
  }

  protected final def writeOuts(chunk: Int): Unit = {
    var ch = 0
    while (ch < numChannels) {
      val out     = outlets(ch)
      val bufOut  = outBufs(ch)
      if (bufOut != null) {
        if (chunk > 0) {
          bufOut.size = chunk
          push(out, bufOut)
        } else {
          bufOut.release()
        }
        outBufs(ch) = null
      }
      ch += 1
    }
  }

  override final def onDownstreamFinish(): Unit =
    if (shape.outlets.forall(isClosed(_))) {
      logStream(s"completeStage() $this")
      completeStage()
    }

  protected final def canWrite: Boolean =
    shape.outlets.forall(out => isClosed(out) || isAvailable(out))

  override final def onPull(): Unit =
    if (numChannels == 1 || canWrite) process()
}
