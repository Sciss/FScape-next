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

import java.net.URI

import akka.stream.Shape
import de.sciss.asyncfile
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.lucre.graph.{ImageFileOut => IF}
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.{Handlers, ImageFileOutImpl}
import de.sciss.numbers.Implicits._

import scala.math.{max, min}

trait ImageFileOutReadsSpec[S <: Shape] extends ImageFileOutImpl[S] {
  _: Handlers[S] =>

  protected def hWidth        : InIAux
  protected def hHeight       : InIAux
  protected def hFileType     : InIAux
  protected def hSampleFormat : InIAux
  protected def hQuality      : InIAux

  protected def fileOrTemplate: URI

  protected def tryObtainSpec(): Boolean = {
    val ok =
      hWidth        .hasNext &&
      hHeight       .hasNext &&
      hFileType     .hasNext &&
      hSampleFormat .hasNext &&
      hQuality      .hasNext

    if (ok) {
      val width     = max(1, hWidth .next())
      val height    = max(1, hHeight.next())
      val fileType  = {
        val i = hFileType.next()
        if (i < 0) {
          import asyncfile.Ops._
          val ext = fileOrTemplate.extL
          ImageFile.Type.writable.find(_.extensions.contains(ext)).getOrElse(ImageFile.Type.PNG)
        } else {
          ImageFile.Type(min(IF.maxFileTypeId, i))
        }
      }
      val sampleFormat = {
        val i = hSampleFormat.next().clip(0, IF.maxSampleFormatId)
        ImageFile.SampleFormat(i)
      }
      val quality = hQuality.next().clip(0, 100)

      val spec = ImageFile.Spec(fileType = fileType, sampleFormat = sampleFormat, width = width, height = height,
        numChannels = numChannels, quality = quality)
      initSpec(spec)
    }
    ok
  }
}
