/*
 *  ImageFileOutReadsSpec.scala
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

package de.sciss.fscape.lucre.stream.impl

import akka.stream.Shape
import akka.stream.stage.InHandler
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.lucre.graph.{ImageFileOut => IF}
import de.sciss.fscape.stream.InI
import de.sciss.fscape.stream.impl.{ImageFileOutImpl, NodeImpl}

trait ImageFileOutReadsSpec[S <: Shape] extends ImageFileOutImpl[S] {
  logic: NodeImpl[S] =>

  protected def canReadImage: Boolean

  private[this] var width       : Int = _
  private[this] var height      : Int = _
  private[this] var fileType    : ImageFile.Type = _
  private[this] var sampleFormat: ImageFile.SampleFormat = _
  private[this] var quality     : Int = _

  private[this] var specDataRem     = 5

  protected final def specReady: Boolean = specDataRem == 0

  private def mkSpec(): Unit = {
    val spec = ImageFile.Spec(fileType = fileType, sampleFormat = sampleFormat, width = width, height = height,
      numChannels = numChannels, quality = quality)
    initSpec(spec)
    if (canReadImage) processImg()
  }

  private final class SpecInHandler(in: InI)(set: Int => Unit) extends InHandler {
    private[this] var done = false

    override def toString: String = s"$logic.$in"

    def onPush(): Unit = if (isInitialized) {
      val b = grab(in)
      if (!done && b.size > 0) {
        val i = b.buf(0)
        set(i)
        done = true
        specDataRem -= 1
        if (specReady) mkSpec()
      }
      b.release()
    }

    def checkPushed(): Unit =
      if (isAvailable(in)) onPush()

    override def onUpstreamFinish(): Unit = {
      if (!done && !isAvailable(in)) super.onUpstreamFinish()
    }

    setHandler(in, this)
  }

  private[this] val specHandlers = new Array[SpecInHandler](5)

  protected final def checkSpecPushed(): Unit =
    specHandlers.foreach(_.checkPushed())

  protected final def setSpecHandlers(inWidth: InI, inHeight: InI, inType: InI, inFormat: InI, inQuality: InI,
                                      fileOrTemplate: File): Unit = {
    val s0 = new SpecInHandler(inWidth )(w => width   = math.max(1, w))
    val s1 = new SpecInHandler(inHeight)(h => height  = math.max(1, h))
    val s2 = new SpecInHandler(inType)({ i =>
      fileType = if (i < 0) {
        val ext = fileOrTemplate.extL
        ImageFile.Type.writable.find(_.extensions.contains(ext)).getOrElse(ImageFile.Type.PNG)
      } else {
        ImageFile.Type(math.min(IF.maxFileTypeId, i))
      }
    })
    val s3 = new SpecInHandler(inFormat)({ i =>
      sampleFormat =
        ImageFile.SampleFormat(math.max(0, math.min(IF.maxSampleFormatId, i)))
    })
    val s4 = new SpecInHandler(inQuality)(q => quality = math.max(0, math.min(100, q)))
    specHandlers(0) = s0
    specHandlers(1) = s1
    specHandlers(2) = s2
    specHandlers(3) = s3
    specHandlers(4) = s4
  }
}
