/*
 *  ImageFileIn.scala
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

import java.net.URI

import akka.stream.Attributes
import de.sciss.fscape.stream.impl.shapes.UniformSourceShape
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileInImpl, NodeHasInitImpl, NodeImpl}

import scala.collection.immutable.{IndexedSeq => Vec}

/*
  XXX TODO: use something like ImgLib2 that supports high resolution images:
  http://imagej.net/ImgLib2_Examples#Example_1_-_Opening.2C_creating_and_displaying_images
 */
object ImageFileIn {
  def apply(file: URI, numChannels: Int)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(layer = b.layer, f = file, numChannels = numChannels)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "ImageFileIn"

  private type Shp = UniformSourceShape[BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(layer: Int, f: URI, numChannels: Int)(implicit ctrl: Control)
    extends BlockingGraphStage[Shp]({
      val p = f.normalize().getPath
      val i = p.lastIndexOf('/') + 1
      val n = p.substring(i)
      s"$name($n)"
    }) {

    val shape: Shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")))

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(name, shape, layer, f, numChannels = numChannels)
  }

  private final class Logic(name: String, shape: Shp, layer: Layer, uri: URI, protected val numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with ImageFileInImpl[Shp] {

    protected val outlets: Vec[OutD] = shape.outlets.toIndexedSeq

    shape.outlets.foreach(setHandler(_, this))

    override protected def init(): Unit = {
      super.init()
      logStream(s"init() $this")
      openImage(uri)
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    protected def freeInputBuffers(): Unit = ()

    protected def process(): Unit = {
      val chunk = math.min(ctrl.blockSize, numFrames - framesRead)
      if (chunk > 0) {
        processChunk(outOff = 0, chunk = chunk)
        writeOuts(chunk)
      }
      if (framesRead == numFrames) {
        logStream(s"completeStage() $this")
        completeStage()
      }
    }
  }
}