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

package de.sciss.fscape
package stream

import akka.stream.Attributes
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile.Spec
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileOutImpl, NodeHasInitImpl, NodeImpl, UniformSinkShape}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

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

  private type Shape = UniformSinkShape[BufD]

  private final class Stage(layer: Layer, f: File, spec: Spec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape: Shape = UniformSinkShape[BufD](Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")))

    def createLogic(attr: Attributes) = new Logic(shape, layer = layer, f = f, spec = spec)
  }

  private final class Logic(shape: Shape, layer: Layer, f: File, protected val spec: Spec)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${f.name})", layer, shape)
    with NodeHasInitImpl with ImageFileOutImpl[Shape] {

    protected val inletsImg: Vec[InD ] = shape.inlets.toIndexedSeq

    shape.inlets.foreach(setHandler(_, this))

    protected def numChannels: Int = spec.numChannels

    override protected def init(): Unit = {
      super.init()
      initSpec(spec)
      openImage(f)
    }

    protected def processImg(): Unit = {
      val chunk = readImgInlets()
      if (chunk > 0) {
        processChunk(inOff = 0, chunk = chunk)
      }
      if (framesWritten == numFrames) {
        logStream(s"completeStage() $this")
        completeStage()
      }
    }
  }
}