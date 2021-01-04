/*
 *  ImageFileSeqOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import java.net.URI

import akka.stream.{Attributes, Outlet}
import de.sciss.fscape.graph.ImageFile.Spec
import de.sciss.fscape.stream.impl.Handlers.{InDMain, InIMain}
import de.sciss.fscape.stream.impl.shapes.In1UniformSinkShape
import de.sciss.fscape.stream.impl.{BlockingGraphStage, Handlers, ImageFileSeqOutImpl, NodeImpl}

import scala.collection.immutable.{Seq => ISeq}

object ImageFileSeqOut {
  def apply(template: URI, spec: Spec, indices: OutI, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val nameL = Util.mkLogicName(name, template)
    val sink  = new Stage(layer = b.layer, template = template, spec = spec, nameL = nameL)
    val stage = b.add(sink)
    b.connect(indices, stage.in0)
    (in zip stage.inlets1).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "ImageFileSeqOut"

  private type Shp = In1UniformSinkShape[BufI, BufD]

  private final class Stage(layer: Layer, template: URI, spec: Spec, nameL: String)
                           (implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](nameL) {

    val shape: Shape = In1UniformSinkShape[BufI, BufD](
      InI(s"$name.indices"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(nameL, shape, layer = layer, template = template, spec = spec)
  }

  private final class Logic(name: String, shape: Shp, layer: Layer, protected val template: URI, val spec: Spec)
                           (implicit ctrl: Control)
    extends Handlers(name, layer, shape)
    with ImageFileSeqOutImpl[Shp] { logic =>

    protected def numChannels: Int = spec.numChannels

    protected val hImg    : Array[InDMain]  = shape.inlets1.iterator.map(InDMain(this, _)).toArray
    protected val hIndices: InIMain         = InIMain(this, shape.in0)

    protected def tryObtainSpec(): Boolean = true

    override protected def init(): Unit = {
      super.init()
      initSpec(spec)
    }

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)
  }
}