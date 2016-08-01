/*
 *  ImageFileOutImpl.scala
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
package impl

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import akka.stream.Shape
import akka.stream.stage.OutHandler
import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.graph.ImageFile.SampleFormat

import scala.collection.immutable.{IndexedSeq => Vec}

/** Common building block for `ImageFileOut` and `ImageFileSeqOut` */
trait ImageFileOutImpl[S <: Shape] extends OutHandler {
  _: StageLogicImpl[S] =>

  // ---- abstract ----

  protected def numChannels : Int
  protected def inBufs      : Array[BufD]
  protected def inlets      : Vec[InD]
  protected def spec        : ImageFile.Spec

  protected def process(): Unit

  // ---- impl ----

  private[this]   val numBands      : Int             = spec.numChannels
  protected final val numFrames     : Int             = spec.width * spec.height
  protected final var framesWritten : Int             = _
  private[this]   val gain          : Double          = 1.0 / (spec.sampleFormat match {
      case SampleFormat.Int8  =>   255.0
      case SampleFormat.Int16 => 65535.0
      case SampleFormat.Float =>     1.0
    })

  private[this]   var pixBuf        : Array[Double]   = new Array(numBands * spec.width)
  protected final var img           : BufferedImage   = _

  /** Resets `framesRead`. */
  protected final def openImage(f: File): Unit = {
    closeImage()
    img         = ImageIO.read(f)
    framesWritten = 0
  }

  protected final def closeImage(): Unit = {
//    pixBuf = null
    if (img != null) {
      img.flush()
      img = null
    }
  }

  protected final def freeInputBuffers(): Unit = {
    var i = 0
    while (i < inBufs.length) {
      if (inBufs(i) != null) {
        inBufs(i).release()
        inBufs(i) = null
      }
      i += 1
    }
  }
}