/*
 *  ImageFileOutImpl.scala
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
package impl

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.{BandedSampleModel, BufferedImage, ComponentColorModel, DataBuffer, Raster}
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import javax.imageio.stream.FileImageOutputStream
import javax.imageio.{IIOImage, ImageIO, ImageTypeSpecifier, ImageWriteParam, ImageWriter}

import akka.stream.Shape
import akka.stream.stage.InHandler
import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.graph.ImageFile.{SampleFormat, Type}

import scala.collection.immutable.{IndexedSeq => Vec}

/** Common building block for `ImageFileOut` and `ImageFileSeqOut` */
trait ImageFileOutImpl[S <: Shape] extends InHandler {
  logic: NodeImpl[S] =>

  // ---- abstract ----

  protected def inlets1     : Vec[InD]
  protected def spec        : ImageFile.Spec

  /** Called when all of `inlets1` are ready. */
  protected def process1(): Unit

  // ---- impl ----

  private[this]   val numChannels   : Int             = spec.numChannels
  private[this]   val bufIns1       : Array[BufD]     = new Array[BufD](numChannels)
  protected final val numFrames     : Int             = spec.width * spec.height
  protected final var framesWritten : Int             = _

  private[this] var imagesWritten = 0
  private[this] var pushed        = 0

//  private /* [this] */ val resultP = Promise[Long]()

  private[this] val (dataType, gain) = spec.sampleFormat match {
    case SampleFormat.Int8  => DataBuffer.TYPE_BYTE   ->   255.0
    case SampleFormat.Int16 => DataBuffer.TYPE_USHORT -> 65535.0
    case SampleFormat.Float => DataBuffer.TYPE_FLOAT  ->     1.0 // XXX TODO --- currently not supported by ImageIO?
  }

  private[this]   var pixBuf: Array[Double]   = new Array(numChannels * spec.width)
  protected final var img   : BufferedImage   = {
    val sm        = new BandedSampleModel(dataType, spec.width, spec.height, spec.numChannels)
    val r         = Raster.createWritableRaster(sm, null)
    val cs        = ColorSpace.getInstance(if (numChannels == 1) ColorSpace.CS_GRAY else ColorSpace.CS_sRGB)
    val hasAlpha  = numChannels == 4
    val cm        = new ComponentColorModel(cs, hasAlpha, false, Transparency.TRANSLUCENT, dataType)
    new BufferedImage(cm, r, false, null)
  }

  private[this] val imgParam: ImageWriteParam = spec.fileType match {
    case Type.PNG => null
    case Type.JPG =>
      val p = new JPEGImageWriteParam(null)
      p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
      p.setCompressionQuality(spec.quality * 0.01f)
      p
  }

  private[this] val writer: ImageWriter = {
    val fmtName = spec.fileType match {
      case Type.PNG => "png"
      case Type.JPG => "jpg"
    }

    val it = ImageIO.getImageWriters(ImageTypeSpecifier.createFromRenderedImage(img), fmtName)
    if (!it.hasNext) throw new IllegalArgumentException(s"No image writer for $spec")
    it.next()
  }

  // ---- Leaf

//  private[this] val asyncCancel = getAsyncCallback[Unit] { _ =>
//    val ex = Cancelled()
//    if (resultP.tryFailure(ex)) failStage(ex)
//  }
//
//  def result: Future[Any] = resultP.future
//
//  def cancel(): Unit = asyncCancel.invoke(())

  // ---- StageLogic and handlers

  override def onPush(): Unit = {
    pushed += 1
    if (pushed == numChannels) {
      pushed = 0
      process1()
    }
  }

  /** Resets `framesWritten`. */
  protected final def openImage(f: File): Unit = {
    closeImage()
    f.delete()
    val out = new FileImageOutputStream(f)
    writer.setOutput(out)
    framesWritten = 0
  }


  override def preStart(): Unit = {
    logStream(s"$this - preStart()")
    shape.inlets.foreach(pull(_))
  }

  override protected def stopped(): Unit = {
    logStream(s"$this - postStop()")
    closeImage()
    pixBuf = null
    if (img != null) {
      img.flush()
      img = null
    }
    freeInputBuffers()
    writer.dispose()
//    resultP.trySuccess(numFrames.toLong * imagesWritten)
  }

  protected final def closeImage(): Unit = if (writer.getOutput != null) {
    try {
      writer.write(null /* meta */ , new IIOImage(img, null /* thumb */ , null /* meta */), imgParam)
      imagesWritten += 1
//    } catch {
//      case NonFatal(ex) =>
//        resultP.tryFailure(ex)
//        throw ex
    } finally {
      writer.reset()
    }
  }

  private def write(x: Int, y: Int, width: Int, offIn: Int): Int = {
    //        println(s"setPixels($x, $y, $width, $height)")
    val r       = img.getRaster
    val offOut  = offIn + width
    var ch      = 0
    val a       = pixBuf
    val nb      = numChannels
    val g       = gain
    while (ch < nb) {
      val b = bufIns1(ch).buf
      var i = ch
      var j = offIn
      while (j < offOut) {
        a(i) = b(j) * g
        i   += nb
        j   += 1
      }
      ch += 1
    }
    r.setPixels(x, y, width, 1, pixBuf)

    offOut
  }

  protected final def processChunk(inOff: Int, chunk: Int): Unit = {
    //      println(s"process(): framesWritten = $framesWritten, numFrames = $numFrames, chunk = $chunk")

    val stop  = framesWritten + chunk
    val w     = img.getWidth
    val x0    = framesWritten % w
    val y0    = framesWritten / w
    val x1    = stop          % w
    val y1    = stop          / w

    //      println(s"IMAGE WRITE chunk = $chunk, x0 = $x0, y0 = $y0, x1 = $x1, y1 = $y1")

    // first (partial) line
    var off0 = write(
      x       = x0,
      y       = y0,
      width   = (if (y1 == y0) x1 else w) - x0,
      offIn   = inOff
    )

    // middle lines
    var y2 = y0 + 1
    while (y2 < y1) {
      off0 = write(
        x       = 0,
        y       = y2,
        width   = w,
        offIn   = off0
      )
      y2 += 1
    }

    // last (partial) line
    if (y1 > y0 && x1 > 0) write(
      x       = 0,
      y       = y1,
      width   = x1,
      offIn   = off0
    )

    var ch = 0
    while (ch < numChannels) {
      bufIns1(ch).release()
      bufIns1(ch) = null
      pull(inlets1(ch))
      ch += 1
    }

    framesWritten += chunk
//    if (framesWritten == numFrames) completeStage()
  }

  protected final def readIns1(): Int = {
    var ch    = 0
    var chunk = 0
    while (ch < numChannels) {
      val bufIn = grab(inlets1(ch))
      bufIns1(ch)  = bufIn
      chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
      ch += 1
    }
    chunk = math.min(chunk, numFrames - framesWritten)
    chunk
  }

  protected final def freeInputBuffers(): Unit = {
    var i = 0
    while (i < bufIns1.length) {
      if (bufIns1(i) != null) {
        bufIns1(i).release()
        bufIns1(i) = null
      }
      i += 1
    }
  }

//  override def onUpstreamFailure(ex: Throwable): Unit = {
//    resultP.failure(ex)
//    super.onUpstreamFailure(ex)
//  }
}