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
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.lucre.stream.impl.ImageFileOutReadsSpec
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileSingleOutImpl, In5UniformSinkShape, NodeImpl}
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

  private final class Logic(shape: Shape, layer: Layer, f: File, protected val numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(s"$name(${f.name})", layer, shape)
      with ImageFileSingleOutImpl[Shape] with ImageFileOutReadsSpec[Shape] { self =>

    protected val inletsImg: Vec[InD] = shape.inlets5.toIndexedSeq

    setImageInHandlers()
    setSpecHandlers(inWidth = shape.in0, inHeight = shape.in1, inType = shape.in2,
      inFormat = shape.in3, inQuality = shape.in4, fileOrTemplate = f)

    override protected def initSpec(spec: ImageFile.Spec): Unit = {
      super.initSpec(spec)
      openImage(f)
    }

    override protected def launch(): Unit = {
      super.launch()
      checkImagePushed()
      checkSpecPushed()
    }
  }
}