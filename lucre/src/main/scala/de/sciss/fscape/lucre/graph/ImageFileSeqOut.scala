/*
 *  ImageFileSeqOut.scala
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

package de.sciss.fscape.lucre.graph

import de.sciss.file.File
import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.graph.ImageFile.{SampleFormat, Type}
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.{GE, Lazy, UGen, UGenGraph, UGenIn, UGenSource, Util, lucre, stream}
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFileSeqOut {
  /** Converts an image file type to a unique id that can be parsed by the UGen. */
  def id(in: Type): Int = in.id

  /** Converts a sample format to a unique id that can be parsed by the UGen. */
  def id(in: SampleFormat): Int = in.id

  /** Recovers an image file type from an id. Throws an exception if the id is invalid. */
  def fileType(id: Int): Type = ImageFileOut.fileType(id)

  def maxFileTypeId: Int = ImageFileOut.maxFileTypeId

  /** Recovers a sample format from an id. Throws an exception if the id is invalid. */
  def sampleFormat(id: Int): SampleFormat = ImageFileOut.sampleFormat(id)

  def maxSampleFormatId: Int = ImageFileOut.maxSampleFormatId

  final case class WithFile(template: File, in: GE, width: GE, height: GE, fileType: GE,
                            sampleFormat: GE, quality: GE, indices: GE)
    extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, width.expand +: height.expand +: fileType.expand +: sampleFormat.expand +: quality.expand +:
        indices.expand +: in.expand.outputs)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, aux = Aux.FileOut(template) :: Nil, isIndividual = true)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val width +: height +: fileType +: sampleFormat +: quality +: indices +: in = args
      lucre.stream.ImageFileSeqOut(template = template, width = width.toInt, height = height.toInt,
        fileType = fileType.toInt, sampleFormat = sampleFormat.toInt, quality = quality.toInt,
        indices = indices.toInt,
        in = in.map(_.toDouble))
    }

    override def productPrefix: String = s"ImageFileSeqOut$$WithFile"
  }
}
/** A graph element that creates a UGen writing to a file
  * sequence designated by an object attribute with a given `key` and the
  * value being an `Artifact`.
  *
  * If the given file-type `GE` is `-1`, the type is determined by this
  * artifact. For example, if the artifact's path ends in `".png"`, the PNG
  * format will used, if the path ends in `".jpg"`, then JPEG will be used.
  * The default is PNG.
  *
  * @param key          the key into the enclosing object's attribute map,
  *                     pointing to an `Artifact` for the file template.
  *                     The artifact's file name is taken as a ''template''. Either that file contains a single
  *                     placeholder for `java.util.Formatter` syntax,
  *                     such as `%d` to insert an integer number. Or alternatively, if the file name does
  *                     not contain a `%` character but a digit or a sequence of digits, those digits
  *                     will be replaced by `%d` to produce a valid template.
  *                     Therefore, if the template is `foo-123.jpg` and the indices contain `4` and `5`,
  *                     then the UGen will write the images `foo-4` and `foo-5` (the placeholder `123` is
  *                     irrelevant).
  * @param in           the signal to write
  * @param width        image's width in pixels
  * @param height       image's height in pixels
  * @param fileType     a file type id as given by `ImageFileSeqOut.id()`. The default
  *                     is `-1` (auto-detect).
  * @param sampleFormat a sample format id as given by `ImageFileSeqOut.id()`. The default
  *                     is `0` (8-bit Int).
  * @param quality      the compression quality for a lossy format such as JPG. The
  *                     default is `80`.
  */
final case class ImageFileSeqOut(key: String, in: GE, width: GE, height: GE, fileType: GE = -1,
                                 sampleFormat: GE = 0, quality: GE = 80, indices: GE)
  extends Lazy.Expander[Unit] {

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub = UGenGraphBuilder.get(b)
    ub.requestInput(Input.Attribute(key)).peer.fold[Unit] {
      sys.error(s"Missing Attribute $key")
    } {
      //      case a: ImageCue =>
      //        val spec = a.spec
      //        ImageFileSeqOut.WithFile(file = a.artifact, in = in, fileType = ImageFileSeqOut.id(spec.fileType),
      //          sampleFormat = ImageFileSeqOut.id(spec.sampleFormat), sampleRate = spec.sampleRate)

      case f: File =>
        val t = Util.mkTemplate(f)
        ImageFileSeqOut.WithFile(template = t, in = in, width = width, height = height, fileType = fileType,
          sampleFormat = sampleFormat, quality = quality, indices = indices)

      case other =>
        sys.error(s"$this - requires Artifact value, found $other")
    }
  }
}