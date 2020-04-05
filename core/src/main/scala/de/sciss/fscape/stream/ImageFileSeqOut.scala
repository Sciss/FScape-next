/*
 *  ImageFileSeqOut.scala
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

package de.sciss.fscape
package stream

import akka.stream.Attributes
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile.Spec
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileSeqOutImpl, In1UniformSinkShape, NodeHasInitImpl, NodeImpl}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

object ImageFileSeqOut {
  def apply(template: File, spec: Spec, indices: OutI, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(layer = b.layer, template = template, spec = spec)
    val stage = b.add(sink)
    b.connect(indices, stage.in0)
    (in zip stage.inlets1).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "ImageFileSeqOut"

  private type Shp = In1UniformSinkShape[BufI, BufD]

  private final class Stage(layer: Layer, template: File, spec: Spec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](s"$name(${template.name})") {

    val shape: Shape = In1UniformSinkShape[BufI, BufD](
      InI(s"$name.indices"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, template = template, spec = spec)
  }

  private final class Logic(shape: Shp, layer: Layer, protected val template: File, val spec: Spec)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${template.name})", layer, shape)
    with NodeHasInitImpl with ImageFileSeqOutImpl[Shp] { logic =>

    protected def numChannels: Int = spec.numChannels

    protected val inletsImg   : Vec[InD]  = shape.inlets1.toIndexedSeq
    protected val inletIndices:     InI   = shape.in0

    protected def specReady: Boolean = true

    override protected def init(): Unit = {
      super.init()
      initSpec(spec)
    }

    override protected def launch(): Unit = {
      super.launch()
      checkImagePushed()
      checkIndicesPushed()
    }

    setImageInHandlers()
    setIndicesHandler()
  }
}