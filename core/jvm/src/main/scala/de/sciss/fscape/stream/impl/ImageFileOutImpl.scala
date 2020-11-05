/*
 *  ImageFileOutImpl.scala
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

package de.sciss.fscape.stream.impl

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.{BandedSampleModel, BufferedImage, ComponentColorModel, DataBuffer, Raster}
import java.net.URI

import akka.stream.{Inlet, Shape}
import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.graph.ImageFile.{SampleFormat, Type}
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.impl.Handlers.InDMain
import de.sciss.fscape.stream.impl.logic.WindowedMultiInOut
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import javax.imageio.stream.FileImageOutputStream
import javax.imageio.{IIOImage, ImageIO, ImageTypeSpecifier, ImageWriteParam, ImageWriter}

import scala.math.min

/** Common building block for `ImageFileOut` and `ImageFileSeqOut` */
trait ImageFileOutImpl[S <: Shape] extends NodeHasInitImpl with WindowedMultiInOut {
  _: Handlers[S] =>

  // ---- abstract ----

  protected def hImg: Array[InDMain]

  protected def numChannels: Int

  // ---- impl ----

  protected final var numFrames     : Int             = _
  protected final var framesWritten : Int             = _
  protected final var gain          : Double          = _

  private[this] var _imagesWritten = 0

  protected final def imagesWritten: Int = _imagesWritten

  //  private /* [this] */ val resultP = Promise[Long]()

//  protected final def specReady: Boolean = _specReady

  protected def initSpec(spec: ImageFile.Spec): Unit = {
    require (numChannels == spec.numChannels)
    numFrames = spec.width * spec.height
    val (dataType, _gain) = spec.sampleFormat match {
      case SampleFormat.Int8  => DataBuffer.TYPE_BYTE   ->   255.0
      case SampleFormat.Int16 => DataBuffer.TYPE_USHORT -> 65535.0
      case SampleFormat.Float => DataBuffer.TYPE_FLOAT  ->     1.0 // XXX TODO --- currently not supported by ImageIO?
    }
    gain    = _gain
    pixBuf  = new Array(numChannels * spec.width)

    val sm        = new BandedSampleModel(dataType, spec.width, spec.height, spec.numChannels)
    val r         = Raster.createWritableRaster(sm, null)
    val cs        = ColorSpace.getInstance(if (numChannels == 1) ColorSpace.CS_GRAY else ColorSpace.CS_sRGB)
    val hasAlpha  = numChannels == 4
    val cm        = new ComponentColorModel(cs, hasAlpha, false, Transparency.TRANSLUCENT, dataType)
    img = new BufferedImage(cm, r, false, null)

    imgParam = spec.fileType match {
      case Type.PNG => null
      case Type.JPG =>
        val p = new JPEGImageWriteParam(null)
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
        p.setCompressionQuality(spec.quality * 0.01f)
        p
    }

    writer = {
      val fmtName = spec.fileType match {
        case Type.PNG => "png"
        case Type.JPG => "jpg"
      }

      val it = ImageIO.getImageWriters(ImageTypeSpecifier.createFromRenderedImage(img), fmtName)
      if (!it.hasNext) throw new IllegalArgumentException(s"No image writer for $spec")
      it.next()
    }
  }

  // holds one line of pixels
  private[this]   var pixBuf: Array[Double]   = _

  protected final var img   : BufferedImage   = _

  private[this] var imgParam: ImageWriteParam = _
  private[this] var writer: ImageWriter = _

  // ---- by default, assume we are a sink ----

  protected def outAvailable: Int     = Int.MaxValue
  protected def outDone     : Boolean = throw new UnsupportedOperationException
  protected def flushOut()  : Boolean = true

  protected def readWinSize : Long    = numFrames
  protected def writeWinSize: Long    = 0L

  protected def writeFromWindow(n: Int): Unit =
    throw new UnsupportedOperationException

  protected def mainInAvailable: Int = {
    val a   = hImg
    var res = Int.MaxValue
    var ch  = 0
    while (ch < a.length) {
      res = min(res, a(ch).available)
      ch += 1
    }
    res
  }

  protected def mainInDone: Boolean = {
    val a = hImg
    var ch = 0
    while (ch < a.length) {
      if (a(ch).isDone) return true
      ch += 1
    }
    false
  }

  protected def isHotIn(inlet: Inlet[_]): Boolean = true

  /** Resets `framesWritten`. */
  protected final def openImage(uri: URI): Unit = {
    closeImage()
    val f = new File(uri) // XXX TODO
    f.delete()
    val out = new FileImageOutputStream(f)
    writer.setOutput(out)
    framesWritten = 0
  }

  override protected def stopped(): Unit = {
    super.stopped()
    logStream(s"$this - postStop()")
    if (writer != null) {
      closeImage()
      writer.dispose()
      writer = null
    }
    pixBuf = null
    if (img != null) {
      img.flush()
      img = null
    }
//    resultP.trySuccess(numFrames.toLong * imagesWritten)
  }

  protected final def closeImage(): Unit = if (writer.getOutput != null) {
    try {
      writer.write(null /* meta */ , new IIOImage(img, null /* thumb */ , null /* meta */), imgParam)
      _imagesWritten += 1
//    } catch {
//      case NonFatal(ex) =>
//        resultP.tryFailure(ex)
//        throw ex
    } finally {
      writer.reset()
    }
  }

  private def write(x: Int, y: Int, width: Int): Unit = {
    //        println(s"setPixels($x, $y, $width, $height)")
    val r       = img.getRaster
    var ch      = 0
    val a       = pixBuf
    val nb      = numChannels
    val g       = gain
    while (ch < nb) {
      val in    = hImg(ch)
      val b     = in.array
      var i     = ch
      var offIn = in.offset
      val stop  = offIn + width
      while (offIn < stop) {
        a(i)    = b(offIn) * g
        i      += nb
        offIn  += 1
      }
      in.advance(width)
      ch += 1
    }
    r.setPixels(x, y, width, 1, pixBuf)
  }

  protected final def readIntoWindow(chunk: Int): Unit = {
    //      println(s"process(): framesWritten = $framesWritten, numFrames = $numFrames, chunk = $chunk")

    val stop  = framesWritten + chunk
    val w     = img.getWidth
    val x0    = framesWritten % w
    val y0    = framesWritten / w
    val x1    = stop          % w
    val y1    = stop          / w

    // println(s"IMAGE WRITE chunk = $chunk, x0 = $x0, y0 = $y0, x1 = $x1, y1 = $y1; readOff $readOff / $numFrames")

    // first (partial) line
    write(
      x       = x0,
      y       = y0,
      width   = (if (y1 == y0) x1 else w) - x0,
    )

    // middle lines
    var y2 = y0 + 1
    while (y2 < y1) {
      write(
        x       = 0,
        y       = y2,
        width   = w,
      )
      y2 += 1
    }

    // last (partial) line
    if (y1 > y0 && x1 > 0) write(
      x       = 0,
      y       = y1,
      width   = x1,
    )

    framesWritten += chunk
  }
}