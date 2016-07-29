/*
 *  ImageFileIn.scala
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

import java.awt.image.{BufferedImage, DataBuffer}
import javax.imageio.ImageIO

import akka.stream.Attributes
import akka.stream.stage.{GraphStageLogic, OutHandler}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, StageLogicImpl, UniformSourceShape}

import scala.collection.immutable.{IndexedSeq => Vec}

/*
  XXX TODO: use something like ImgLib2 that supports high resolution images:
  http://imagej.net/ImgLib2_Examples#Example_1_-_Opening.2C_creating_and_displaying_images
 */
object ImageFileIn {
  def apply(file: File, numChannels: Int)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(file, numChannels = numChannels)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "ImageFileIn"

  private type Shape = UniformSourceShape[BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(f: File, numChannels: Int)(implicit ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")))

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape, f, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, f: File, numChannels: Int)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${f.name})", shape) with OutHandler {

    private[this] var img       : BufferedImage = _
    private[this] var numBands  : Int           = _
    private[this] var buf       : Array[Double] = _
    private[this] var bufSize   : Int           = _
    private[this] var numFrames : Int           = _
    private[this] var gain      : Double        = _

    private[this] var framesRead  = 0
    private[this] val outBuffers  = new Array[BufD](numChannels)

    shape.outlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      logStream(s"preStart() $this")
      img         = ImageIO.read(f)
      numBands    = img.getSampleModel.getNumBands
      if (numBands != numChannels) {
        Console.err.println(s"Warning: ImageIn - channel mismatch (file has $numBands, UGen has $numChannels)")
      }
      numFrames   = img.getWidth * img.getHeight
      bufSize     = numBands * img.getWidth
      buf         = new Array(bufSize)

      val gainR = img.getSampleModel.getDataType match {
        case DataBuffer.TYPE_BYTE   =>   255.0
        case DataBuffer.TYPE_USHORT => 65535.0
        case DataBuffer.TYPE_FLOAT  =>     1.0
      }
      gain = 1.0 / gainR
    }

    override def postStop(): Unit = {
      logStream(s"postStop() $this")
      buf = null
      img.flush()
      img = null
    }

    override def onDownstreamFinish(): Unit =
      if (shape.outlets.forall(isClosed(_))) {
        logStream(s"completeStage() $this")
        completeStage()
      }

    override def onPull(): Unit =
      if (numChannels == 1 || shape.outlets.forall(out => isClosed(out) || isAvailable(out))) process()

    private def process(): Unit = {
      val chunk = math.min(ctrl.blockSize, numFrames - framesRead)
      if (chunk == 0) {
        logStream(s"completeStage() $this")
        completeStage()
      } else {
        def read(x: Int, y: Int, width: Int, height: Int, offIn: Int): Int = {
          val sz      = width * height
          val offOut  = offIn + sz
          val r       = img.getRaster
          r.getPixels(x, y, width, height, buf)
          var ch  = 0
          val a   = buf
          val nb  = numBands
          val g   = gain
          while (ch < numChannels) {
            val out = shape.out(ch)
            if (!isClosed(out)) {
              if (outBuffers(ch) == null) outBuffers(ch) = ctrl.borrowBufD()
              val bufOut  = outBuffers(ch)
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
          offIn   = 0
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

        var ch = 0
        while (ch < numChannels) {
          val out     = shape.out(ch)
          val bufOut  = outBuffers(ch)
          if (bufOut != null) {
            bufOut.size = chunk
            push(out, bufOut)
            outBuffers(ch) = null
          }
          ch += 1
        }
        framesRead += chunk
      }
    }
  }
}