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

import akka.stream.Attributes
import akka.stream.stage.GraphStageLogic
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileInImpl, StageLogicImpl, UniformSourceShape}

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

  private final class Logic(shape: Shape, f: File, protected val numChannels: Int)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${f.name})", shape) with ImageFileInImpl[Shape] {

    protected val outBufs  = new Array[BufD](numChannels)
    protected val outlets     = shape.outlets.toIndexedSeq

    shape.outlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      logStream(s"preStart() $this")
      openImage(f)
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
        writeOuts   (chunk)
      }
    }
  }
}