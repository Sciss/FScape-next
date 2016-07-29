/*
 *  ImageFileSeqIn.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, UniformFanOutShape}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileInImpl, StageLogicImpl}

import scala.collection.immutable.{IndexedSeq => Vec}

/*
  XXX TODO: use something like ImgLib2 that supports high resolution images:
  http://imagej.net/ImgLib2_Examples#Example_1_-_Opening.2C_creating_and_displaying_images
 */
object ImageFileSeqIn {
  def apply(template: File, numChannels: Int, indices: OutI)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(template, numChannels = numChannels)
    val stage   = b.add(source)
    b.connect(indices, stage.in)
    stage.outArray.toIndexedSeq
  }

  private final val name = "ImageFileSeqIn"

  private type Shape = UniformFanOutShape[BufI, BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(template: File, numChannels: Int)(implicit ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${template.name})") {

    val shape: Shape = UniformFanOutShape(
      inlet   = InI(s"$name.indices"),
      outlets = Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")): _*
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape, template, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, template: File, protected val numChannels: Int)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${template.name})", shape) with ImageFileInImpl[Shape] {

    protected val outBuffers  = new Array[BufD](numChannels)
    protected val outlets     = shape.outArray.toIndexedSeq

    shape.outlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      logStream(s"preStart() $this")
      ??? // openImage(f)
    }

    override def postStop(): Unit = {
      logStream(s"postStop() $this")
      closeImage()
    }

    protected def process(): Unit = {
      val chunk = math.min(ctrl.blockSize, numFrames - framesRead)
      if (chunk == 0) {
        logStream(s"completeStage() $this")
        completeStage()
      } else {
        processChunk(chunk)
        pushBuffers (chunk)
      }
    }
  }
}