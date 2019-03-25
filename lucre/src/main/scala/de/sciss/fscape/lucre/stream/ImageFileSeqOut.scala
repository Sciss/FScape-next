/*
 *  ImageFileSeqOut.scala
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
import de.sciss.fscape.lucre.stream.impl.ImageFileOutReadsSpec
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileSeqOutImpl, In6UniformSinkShape, NodeImpl}
import de.sciss.fscape.stream.{BufD, BufI, Builder, Control, InD, InI, Layer, OutD, OutI}
import de.sciss.synth.UGenSource.Vec

import scala.collection.immutable.{Seq => ISeq}

object ImageFileSeqOut {
  def apply(template: File, width: OutI, height: OutI, fileType: OutI, sampleFormat: OutI, quality: OutI, indices: OutI,
            in: ISeq[OutD])
           (implicit b: Builder): Unit = {
    val stage0  = new Stage(layer = b.layer, template = template, numChannels = in.size)
    val stage   = b.add(stage0)
    b.connect(width       , stage.in0)
    b.connect(height      , stage.in1)
    b.connect(fileType    , stage.in2)
    b.connect(sampleFormat, stage.in3)
    b.connect(quality     , stage.in4)
    b.connect(indices     , stage.in5)
    (in zip stage.inlets6).foreach { case (output, input) =>
      b.connect(output, input)
    }
    //    stage.out
  }

  private final val name = "ImageFileSeqOut"

  private type Shape = In6UniformSinkShape[BufI, BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer, template: File, numChannels: Int)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${template.name})") {

    val shape: Shape = In6UniformSinkShape(
      InI (s"$name.width"       ),
      InI (s"$name.height"      ),
      InI (s"$name.fileType"    ),
      InI (s"$name.sampleFormat"),
      InI (s"$name.quality"     ),
      InI (s"$name.indices"     ),
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, template, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, layer: Layer, protected val template: File, protected val numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(s"$name(${template.name})", layer, shape)
      with ImageFileSeqOutImpl[Shape] with ImageFileOutReadsSpec[Shape] { self =>

    protected val inletsImg   : Vec[InD]  = shape.inlets6.toIndexedSeq
    protected val inletIndices:     InI   = shape.in5

    setImageInHandlers()
    setIndicesHandler()
    setSpecHandlers(inWidth = shape.in0, inHeight = shape.in1, inType = shape.in2,
      inFormat = shape.in3, inQuality = shape.in4, fileOrTemplate = template)

  }
}