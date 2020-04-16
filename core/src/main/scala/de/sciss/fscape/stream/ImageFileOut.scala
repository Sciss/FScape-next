/*
 *  ImageFileOut.scala
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

import akka.stream.{Attributes, Outlet}
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile.Spec
import de.sciss.fscape.stream.impl.Handlers.InDMain
import de.sciss.fscape.stream.impl.shapes.UniformSinkShape
import de.sciss.fscape.stream.impl.{BlockingGraphStage, Handlers, ImageFileSingleOutImpl, NodeImpl}

import scala.collection.immutable.{Seq => ISeq}

object ImageFileOut {
  def apply(file: File, spec: Spec, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(layer = b.layer, f = file, spec = spec)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "ImageFileOut"

  private type Shp = UniformSinkShape[BufD]

  private final class Stage(layer: Layer, f: File, spec: Spec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](s"$name(${f.name})") {

    val shape: Shape = UniformSinkShape[BufD](Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")))

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer = layer, f = f, spec = spec)
  }

  private final class Logic(shape: Shp, layer: Layer, f: File, protected val spec: Spec)(implicit ctrl: Control)
    extends Handlers(s"$name(${f.name})", layer, shape)
    with ImageFileSingleOutImpl[Shp] {

    protected val hImg: Array[InDMain] = shape.inlets.iterator.map(InDMain(this, _)).toArray

    protected def tryObtainSpec(): Boolean = true

    protected def numChannels: Int = spec.numChannels

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)

    override protected def init(): Unit = {
      super.init()
      initSpec(spec)
      openImage(f)
    }
  }
}