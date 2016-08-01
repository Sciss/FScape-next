/*
 *  ImageFileOutImpl.scala
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
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

/** Common building block for `ImageFileOut` and `ImageFileSeqOut` */
trait ImageFileOutImpl[S <: Shape] extends InHandler {
  logic: StageLogicImpl[S] =>

  // ---- abstract ----

  protected def bufIns      : Array[BufD]
  protected def inlets      : Vec[InD]
  protected def spec        : ImageFile.Spec

  protected def process(): Unit

  // ---- impl ----

  private[this]   val numChannels   : Int             = spec.numChannels
  protected final val numFrames     : Int             = spec.width * spec.height
  protected final var framesWritten : Int             = _

  private /* [this] */ val result = Promise[Long]()

  private[this] val (dataType, gain) = spec.sampleFormat match {
    case SampleFormat.Int8  => DataBuffer.TYPE_BYTE   ->   255.0
    case SampleFormat.Int16 => DataBuffer.TYPE_USHORT -> 65535.0
    case SampleFormat.Float => DataBuffer.TYPE_FLOAT  ->     1.0 // XXX TODO --- currently not supported by ImageIO?
  }

  private[this]   var pixBuf        : Array[Double]   = new Array(numChannels * spec.width)
  protected final var img           : BufferedImage   = {
    val sm        = new BandedSampleModel(dataType, spec.width, spec.height, spec.numChannels)
    val r         = Raster.createWritableRaster(sm, null)
    val cs        = ColorSpace.getInstance(if (numChannels == 1) ColorSpace.CS_GRAY else ColorSpace.CS_sRGB)
    val hasAlpha  = numChannels == 4
    val cm        = new ComponentColorModel(cs, hasAlpha, false, Transparency.TRANSLUCENT, dataType)
    new BufferedImage(cm, r, false, null)
  }

  private[this] val imgParam: ImageWriteParam= spec.fileType match {
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

  /** Resets `framesWritten`. */
  protected final def openImage(f: File): Unit = {
    closeImage()
    val out = new FileImageOutputStream(f)
    writer.reset()
    writer.setOutput(out)
    framesWritten = 0
  }


  override def preStart(): Unit = {
    val asyncCancel = getAsyncCallback[Unit] { _ =>
      val ex = Cancelled()
      if (result.tryFailure(ex)) failStage(ex)
    }
    ctrl.addLeaf(new Leaf {
      def result: Future[Any] = logic.result.future

      def cancel(): Unit = asyncCancel.invoke(())
    })

    logStream(s"$this - preStart()")

    shape.inlets.foreach(pull(_))
  }

  override def postStop(): Unit = {
    logStream(s"$this - postStop()")
    pixBuf = null
    img    = null
    freeInputBuffers()
    writer.dispose()
  }

  protected final def closeImage(): Unit = {
    try {
      writer.write(null /* meta */ , new IIOImage(img, null /* thumb */ , null /* meta */), imgParam)
      result.trySuccess(numFrames)
    } catch {
      case NonFatal(ex) =>
        result.tryFailure(ex)
    }
    if (img != null) {
      img.flush()
      img = null
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
      val b = bufIns(ch).buf
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
//    var ch    = 0
//    var chunk = 0
//    while (ch < numChannels) {
//      val bufIn = grab(inlets(ch))
//      bufIns(ch)  = bufIn
//      chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
//      ch += 1
//    }
//    chunk = math.min(chunk, numFrames - framesWritten)

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
      bufIns(ch).release()
      pull(inlets(ch))
      ch += 1
    }

    framesWritten += chunk
//    if (framesWritten == numFrames) completeStage()
  }

  protected final def freeInputBuffers(): Unit = {
    var i = 0
    while (i < bufIns.length) {
      if (bufIns(i) != null) {
        bufIns(i).release()
        bufIns(i) = null
      }
      i += 1
    }
  }

  override def onUpstreamFailure(ex: Throwable): Unit = {
    result.failure(ex)
    super.onUpstreamFailure(ex)
  }
}