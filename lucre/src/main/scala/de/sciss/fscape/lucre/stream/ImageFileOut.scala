/*
 *  ImageFileOut.scala
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

package de.sciss.fscape.lucre.stream

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.logStream
import de.sciss.fscape.lucre.graph.{ImageFileOut => IF}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileOutImpl, In5UniformSinkShape, NodeImpl}
import de.sciss.fscape.stream.{BufD, BufI, Builder, Control, InD, InI, Layer, OutD, OutI}
import de.sciss.synth.UGenSource.Vec

import scala.collection.immutable.{Seq => ISeq}

object ImageFileOut {
  def apply(file: File, width: OutI, height: OutI, fileType: OutI, sampleFormat: OutI, quality: OutI, in: ISeq[OutD])
           (implicit b: Builder): Unit = {
    val stage0  = new Stage(layer = b.layer, f = file, numChannels = in.size)
    val stage   = b.add(stage0)
    b.connect(width       , stage.in0)
    b.connect(height      , stage.in1)
    b.connect(fileType    , stage.in2)
    b.connect(sampleFormat, stage.in3)
    b.connect(quality     , stage.in4)
    (in zip stage.inlets5).foreach { case (output, input) =>
      b.connect(output, input)
    }
//    stage.out
  }

  private final val name = "ImageFileOut"

  private type Shape = In5UniformSinkShape[BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer, f: File, numChannels: Int)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape: Shape = In5UniformSinkShape(
      InI (s"$name.width"       ),
      InI (s"$name.height"      ),
      InI (s"$name.fileType"    ),
      InI (s"$name.sampleFormat"),
      InI (s"$name.quality"     ),
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, f, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, layer: Layer, f: File, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(s"$name(${f.name})", layer, shape)
      with ImageFileOutImpl[Shape] { self =>

    protected val inletsImg: Vec[InD ] = shape.inlets5.toIndexedSeq

    shape.inlets5.foreach(setHandler(_, this))

    private[this] var width       : Int = _
    private[this] var height      : Int = _
    private[this] var fileType    : ImageFile.Type = _
    private[this] var sampleFormat: ImageFile.SampleFormat = _
    private[this] var quality     : Int = _

    private[this] var specDataRem     = 5
    private[this] var imgInletsReady  = false

    @inline private[this] def specReady: Boolean = specDataRem == 0

    private def mkSpec(): Unit = {
      val spec = ImageFile.Spec(fileType = fileType, sampleFormat = sampleFormat, width = width, height = height,
        numChannels = numChannels, quality = quality)
      initSpec(spec)
      openImage(f)
      if (imgInletsReady) processImg()
    }

    private final class SpecInHandler(in: InI)(set: Int => Unit) extends InHandler {
      private[this] var done = false

      override def toString: String = s"$self.$in"

      def onPush(): Unit = {
        val b = grab(in)
        if (!done && b.size > 0) {
          val i = b.buf(0)
          set(i)
          done = true
          specDataRem -= 1
          if (specReady) mkSpec()
        }
        b.release()
      }

      override def onUpstreamFinish(): Unit = {
        if (specDataRem > 0) super.onUpstreamFinish()
      }

      setHandler(in, this)
    }

    new SpecInHandler(shape.in0)(w => width   = math.max(1, w))
    new SpecInHandler(shape.in1)(h => height  = math.max(1, h))
    new SpecInHandler(shape.in2)({ i =>
      fileType = if (i < 0) {
        val ext = f.extL
        ImageFile.Type.writable.find(_.extensions.contains(ext)).getOrElse(ImageFile.Type.PNG)
      } else {
        ImageFile.Type(math.min(IF.maxFileTypeId, i))
      }
    })
    new SpecInHandler(shape.in3)({ i =>
      sampleFormat =
        ImageFile.SampleFormat(math.max(0, math.min(IF.maxSampleFormatId, i)))
    })
    new SpecInHandler(shape.in4)(q => quality = math.max(0, math.min(100, q)))

    protected def processImg(): Unit = {
      if (specReady) {
        val chunk = readIns1()
        if (chunk > 0) {
          processChunk(inOff = 0, chunk = chunk)
        }
        if (framesWritten == numFrames) {
          logStream(s"completeStage() $this")
          completeStage()
        }
      } else {
        imgInletsReady = true
      }
    }
  }
}