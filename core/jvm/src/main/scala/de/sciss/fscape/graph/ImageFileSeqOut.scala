/*
 *  ImageFileSeqOut.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}

import java.net.URI
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFileSeqOut extends ProductReader[ImageFileSeqOut] {
  override def read(in: RefMapIn, key: String, arity: Int): ImageFileSeqOut = {
    require (arity == 4)
    val _in       = in.readGE()
    val _template = in.readURI()
    val _spec     = in.readProductT[ImageFile.Spec]()
    val _indices  = in.readGE()
    new ImageFileSeqOut(_in, _template, _spec, _indices)
  }
}
/** Writes a sequence of images, taken their data one by one from the input `in`, and writing them
  * to individual files, determining
  * their file names by formatting a `template` with a numeric argument given through `indices`.
  *
  * @param  template  a file which contains a single placeholder for `java.util.Formatter` syntax,
  *                   such as `%d` to insert an integer number. Alternatively, if the file name does
  *                   not contain a `%` character but a digit or a sequence of digits, those digits
  *                   will be replaced by `%d` to produce a valid template.
  *                   Therefore, if the template is `foo-123.jpg` and the indices contain `4` and `5`,
  *                   then the UGen will write the images `foo-4` and `foo-5` (the placeholder `123` is
  *                   irrelevant).
  */
final case class ImageFileSeqOut(in: GE, template: URI, spec: ImageFile.Spec, indices: GE)
  extends UGenSource.ZeroOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, in.expand.outputs :+ indices.expand)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
    val t = Util.mkTemplate(template)
    UGen.ZeroOut(this, inputs = args,
      adjuncts = Adjunct.FileOut(t) :: Adjunct.ImageFileSpec(spec) :: Nil, isIndividual = true)
    ()
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val in :+ indices = args
    val t = Util.mkTemplate(template)
    stream.ImageFileSeqOut(template = t, spec = spec, indices = indices.toInt, in = in.map(_.toDouble))
  }
}