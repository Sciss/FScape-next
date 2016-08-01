/*
 *  ImageFileSeqOut.scala
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

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile.Spec
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileOutImpl, In1UniformSinkShape, StageLogicImpl}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

object ImageFileSeqOut {
  def apply(template: File, spec: Spec, indices: OutI, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(template, spec)
    val stage = b.add(sink)
    b.connect(indices, stage.in0)
    (in zip stage.inlets1).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "ImageFileSeqOut"

  private type Shape = In1UniformSinkShape[BufI, BufD]

  private final class Stage(template: File, spec: Spec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${template.name})") {

    override val shape = In1UniformSinkShape[BufI, BufD](
      InI(s"$name.indices"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes) = new Logic(shape, template, spec)
  }

  private final class Logic(shape: Shape, template: File, val spec: Spec)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${template.name})", shape)
    with ImageFileOutImpl[Shape] { logic =>

    shape.inlets1.foreach(setHandler(_, this))

    protected val inlets1: Vec[InD] = shape.inlets1.toIndexedSeq

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush(${shape.in0})")
        testRead()
      }

      private def testRead(): Unit = {
        ???
//        updateCanRead()
//        if (_canRead) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish(${shape.in0})")
        if ((??? : Boolean) /* _inValid */ || isAvailable({shape.in0})) {
          testRead()
        } else {
          println(s"Invalid aux ${shape.in0}")
          completeStage()
        }
      }
    })

    /** Called when all of `inlets1` are ready. */
    protected def process(): Unit = ???
  }
}