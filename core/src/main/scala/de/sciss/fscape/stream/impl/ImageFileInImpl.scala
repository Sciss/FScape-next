/*
 *  ImageFileInImpl.scala
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
package impl

import java.awt.image.{BufferedImage, DataBuffer}
import javax.imageio.ImageIO

import akka.stream.Shape
import akka.stream.stage.OutHandler
import de.sciss.file.File

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.control.NonFatal

/** Common building block for `ImageFileIn` and `ImageFileSeqIn` */
trait ImageFileInImpl[S <: Shape] extends NodeHasInitImpl with OutHandler {
  _: NodeImpl[S] =>

  // ---- abstract ----

  protected def numChannels : Int
  protected def outlets     : Vec[OutD]

  protected def process(): Unit
  protected def freeInputBuffers(): Unit

  // ---- impl ----

  private[this]   val bufOuts     : Array[BufD]     = new Array(numChannels)
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
    img         = try {
      ImageIO.read(f)
    } catch {
      case NonFatal(ex) =>
        Console.err.println(s"$this - for file $f")
        throw ex
    }
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

  override protected def stopped(): Unit = {
    logStream(s"postStop() $this")
    freeInputBuffers()
    freeOutputBuffers()
    closeImage()
  }

  protected final def freeOutputBuffers(): Unit = {
    var i = 0
    while (i < bufOuts.length) {
      if (bufOuts(i) != null) {
        bufOuts(i).release()
        bufOuts(i) = null
      }
      i += 1
    }
  }

  private[this] def read(x: Int, y: Int, width: Int, offIn: Int): Int = {
    val offOut  = offIn + width
    img.getRaster.getPixels(x, y, width, 1, pixBuf)
    var ch  = 0
    val a   = pixBuf
    val nb  = numBands
    val g   = gain
    while (ch < numChannels) {
      val out = outlets(ch)
      if (!isClosed(out)) {
        if (bufOuts(ch) == null) bufOuts(ch) = control.borrowBufD()
        val bufOut  = bufOuts(ch)
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
          Util.clear(b, offIn, width)
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
    var off0 = read(
      x       = x0,
      y       = y0,
      width   = (if (y1 == y0) x1 else w) - x0,
      offIn   = outOff
    )

    // middle lines
    var y2    = y0 + 1
    while (y2 < y1) {
      off0 = read(
        x       = 0,
        y       = y2,
        width   = w,
        offIn   = off0
      )
      y2 += 1
    }

    // last (partial) line
    if (y1 > y0 && x1 > 0) read(
      x       = 0,
      y       = y1,
      width   = x1,
      offIn   = off0
    )

    framesRead += chunk
  }

  protected final def writeOuts(chunk: Int): Unit = {
    var ch = 0
    while (ch < numChannels) {
      val out     = outlets(ch)
      val bufOut  = bufOuts(ch)
      if (bufOut != null) {
        if (isClosed(out)) {
          // println(s"Wowowo - $out closed")
          bufOut.release()
        } else {
          if (chunk > 0) {
            bufOut.size = chunk
            push(out, bufOut)
          } else {
            bufOut.release()
          }
        }
        bufOuts(ch) = null
      }
      ch += 1
    }
  }

  override final def onDownstreamFinish(): Unit = {
    val all = shape.outlets.forall(isClosed(_))
    logStream(s"onDownstreamFinish() $this - $all")
    if (all) {
      super.onDownstreamFinish()
    } else {
      onPull()
    }
  }

  protected final def canWrite: Boolean =
    shape.outlets.forall(out => isClosed(out) || isAvailable(out))

  override final def onPull(): Unit = {
    val ok = isInitialized && canWrite
    logStream(s"onPull() - $ok - $this")
    if (ok) process()
  }
}
