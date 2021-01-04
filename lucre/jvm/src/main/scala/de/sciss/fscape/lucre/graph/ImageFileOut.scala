/*
 *  ImageFileOut.scala
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

package de.sciss.fscape.lucre.graph

import java.net.URI

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.graph.ImageFile.{SampleFormat, Type}
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream.StreamIn
import de.sciss.fscape.{GE, Lazy, UGen, UGenGraph, UGenIn, UGenSource, lucre, stream}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFileOut {
  /** Converts an image file type to a unique id that can be parsed by the UGen. */
  def id(in: Type): Int = in.id

  /** Converts a sample format to a unique id that can be parsed by the UGen. */
  def id(in: SampleFormat): Int = in.id

  /** Recovers an image file type from an id. Throws an exception if the id is invalid. */
  def fileType(id: Int): Type = (id: @switch) match {
    case Type.PNG.id => Type.PNG
    case Type.JPG.id => Type.JPG
    case other => sys.error(s"Unexpected audio file type id $other")
  }

  def maxFileTypeId: Int = Type.JPG.id

  /** Recovers a sample format from an id. Throws an exception if the id is invalid. */
  def sampleFormat(id: Int): SampleFormat = (id: @switch) match {
    case SampleFormat.Int8  .id => SampleFormat.Int8
    case SampleFormat.Int16 .id => SampleFormat.Int16
    case SampleFormat.Float .id => SampleFormat.Float
    case other => sys.error(s"Unexpected sample format id $other")
  }

  def maxSampleFormatId: Int = SampleFormat.Float.id

  final case class WithFile(file: URI, in: GE, width: GE, height: GE, fileType: GE,
                            sampleFormat: GE, quality: GE)
    extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, width.expand +: height.expand +: fileType.expand +: sampleFormat.expand +: quality.expand +:
        in.expand.outputs)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, args, adjuncts = Adjunct.FileOut(file) :: Nil, isIndividual = true)
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val width +: height +: fileType +: sampleFormat +: quality +: in = args
      lucre.stream.ImageFileOut(uri = file, width = width.toInt, height = height.toInt,
        fileType = fileType.toInt, sampleFormat = sampleFormat.toInt, quality = quality.toInt,
        in = in.map(_.toDouble))
    }

    override def productPrefix: String = s"ImageFileOut$$WithFile"
  }
}
/** A graph element that creates a UGen writing to a file
  * designated by an object attribute with a given `key` and the
  * value being an `Artifact`.
  *
  * If the given file-type `GE` is `-1`, the type is determined by this
  * artifact. For example, if the artifact's path ends in `".png"`, the PNG
  * format will used, if the path ends in `".jpg"`, then JPEG will be used.
  * The default is PNG.
  *
  * @param key          the key into the enclosing object's attribute map,
  *                     pointing to an `Artifact`
  * @param in           the signal to write
  * @param width        image's width in pixels
  * @param height       image's height in pixels
  * @param fileType     a file type id as given by `ImageFileOut.id()`. The default
  *                     is `-1` (auto-detect).
  * @param sampleFormat a sample format id as given by `ImageFileOut.id()`. The default
  *                     is `0` (8-bit Int).
  * @param quality      the compression quality for a lossy format such as JPG. The
  *                     default is `80`.
  */
final case class ImageFileOut(key: String, in: GE, width: GE, height: GE, fileType: GE = -1,
                              sampleFormat: GE = 0, quality: GE = 80)
  extends Lazy.Expander[Unit] {

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub = UGenGraphBuilder.get(b)
    ub.requestInput(Input.Attribute(key)).peer.fold[Unit] {
      sys.error(s"Missing Attribute $key")
    } {
//      case a: ImageCue =>
//        val spec = a.spec
//        ImageFileOut.WithFile(file = a.artifact, in = in, fileType = ImageFileOut.id(spec.fileType),
//          sampleFormat = ImageFileOut.id(spec.sampleFormat), sampleRate = spec.sampleRate)

      case f: URI =>
        ImageFileOut.WithFile(file = f, in = in, width = width, height = height, fileType = fileType,
          sampleFormat = sampleFormat, quality = quality)
        ()

      case other =>
        sys.error(s"$this - requires Artifact value, found $other")
    }
  }
}