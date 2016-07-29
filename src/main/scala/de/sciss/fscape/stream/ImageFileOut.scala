/*
 *  ImageFileOut.scala
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

import java.awt.color.ColorSpace
import java.awt.image.{BandedSampleModel, BufferedImage, ColorModel, DataBuffer, Raster}
import javax.imageio.ImageIO

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.file._
import de.sciss.fscape.graph.ImageFileOut.{FileType, SampleFormat, Spec}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, StageLogicImpl, UniformSinkShape}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

object ImageFileOut {
  def apply(file: File, spec: Spec, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(file, spec)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "ImageFileOut"

  private type Shape = UniformSinkShape[BufD]

  private final class Stage(f: File, spec: Spec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    override val shape = UniformSinkShape[BufD](Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")))

    def createLogic(attr: Attributes) = new Logic(shape, f, spec)
  }

  private final class Logic(shape: Shape, f: File, spec: Spec)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${f.name})", shape) with InHandler { logic =>

    private[this] var img     : BufferedImage = _

    private[this] val bufSize       = spec.width * spec.numChannels
    private[this] var buf           = new Array[Double](bufSize)
    private[this] var pushed        = 0
    private[this] val numChannels   = spec.numChannels
    private[this] val bufIns        = new Array[BufD](spec.numChannels)
    private[this] var framesWritten = 0
    private[this] var numFrames: Int = _

    private /* [this] */ val result = Promise[Long]()

    shape.inlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      require(if (f.exists()) f.isFile && f.canWrite else f.absolute.parent.canWrite)
      val asyncCancel = getAsyncCallback[Unit] { _ =>
        val ex = Cancelled()
        if (result.tryFailure(ex)) failStage(ex)
      }
      ctrl.addLeaf(new Leaf {
        def result: Future[Any] = logic.result.future

        def cancel(): Unit = asyncCancel.invoke(())
      })

      logStream(s"$this - preStart()")
      val dataType  = spec.sampleFormat match {
        case SampleFormat.Int8  => DataBuffer.TYPE_BYTE
        case SampleFormat.Int16 => DataBuffer.TYPE_USHORT
        case SampleFormat.Float => DataBuffer.TYPE_FLOAT  // XXX TODO --- currently not supported by ImageIO?
      }
      // XXX TODO --- which is more efficient - BandedSampleModel or PixelInterleavedSampleModel?
      val sm        = new BandedSampleModel(dataType, spec.width, spec.height, spec.numChannels)
      val r         = Raster.createWritableRaster(sm, null)
      val cs        = ColorSpace.getInstance(if (numChannels == 1) ColorSpace.CS_GRAY else ColorSpace.CS_sRGB)
      val cm        = ??? : ColorModel // new ColorModel()
      numFrames     = spec.width * spec.height
      img           = new BufferedImage(cm, r, false, null)
      shape.inlets.foreach(pull)
    }

    override def postStop(): Unit = {
      logStream(s"$this - postStop()")
      buf = null
      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        ch += 1
      }
      try {
        val fmtName = spec.fileType match {
          case FileType.PNG => "png"
          case FileType.JPG => "jpg"
        }
        ImageIO.write(img, fmtName, f)
        result.trySuccess(numFrames)
      } catch {
        case NonFatal(ex) => result.tryFailure(ex)
      }
    }

    override def onPush(): Unit = {
      pushed += 1
      if (pushed == numChannels) {
        pushed = 0
        process()
      }
    }

    private def process(): Unit = {
      var ch    = 0
      var chunk = 0
      while (ch < numChannels) {
        val bufIn = grab(shape.in(ch))
        bufIns(ch)  = bufIn
        chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
        ch += 1
      }
      chunk = math.min(chunk, numFrames - framesWritten)

      def write(x: Int, y: Int, width: Int, height: Int, offIn: Int): Int = {
        val r       = img.getRaster
        val sz      = width * height
        val offOut  = offIn + sz
        var ch      = 0
        val a       = buf
        val nb      = numChannels
        while (ch < nb) {
          val b = bufIns(ch).buf
          var i = ch
          var j = offIn
          while (j < offOut) {
            a(i) = b(j)
            i   += nb
            j   += 1
          }
          ch += 1
        }
        r.setPixels(x, y, width, height, buf)

        offOut
      }

      val stop  = framesWritten + chunk
      val w     = img.getWidth
      val x0    = framesWritten % w
      val y0    = framesWritten / w
      val x1    = stop       % w
      val y1    = stop       / w

      // first (partial) line
      val off0 = write(
        x       = x0,
        y       = y0,
        width   = (if (y1 == y0) x1 else w) - x0,
        height  = 1,
        offIn   = 0
      )

      // middle lines
      val hMid  = y1 - y0 - 2
      val off1 = if (hMid <= 0) off0 else write(
        x       = 0,
        y       = y0 + 1,
        width   = w,
        height  = hMid,
        offIn   = off0
      )

      // last (partial) line
      if (y1 > y0) write(
        x       = 0,
        y       = y1,
        width   = x1,
        height  = 1,
        offIn   = off1
      )

      ch = 0
      while (ch < numChannels) {
        bufIns(ch).release()
        pull(shape.in(ch))
        ch += 1
      }

      framesWritten += chunk
      if (framesWritten == numFrames) completeStage()
    }

    override def onUpstreamFailure(ex: Throwable): Unit = {
      result.failure(ex)
      super.onUpstreamFailure(ex)
    }
  }
}