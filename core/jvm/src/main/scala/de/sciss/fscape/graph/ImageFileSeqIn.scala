/*
 *  ImageFileSeqIn.scala
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

import java.net.URI

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

//object ImageFileSeqIn {
//  def formatTemplate(template: File, index: Int): File = {
//    val name      = template.name.format(index)
//    val f         = template.parentOption.fold(file(name))(_ / name)
//    f
//  }
//}

/** Reads a sequence of images, outputting them directly one after the other, determining
  * their file names by formatting a `template` with a numeric argument given through `indices`.
  *
  * @param  template  a file which contains a single placeholder for `java.util.Formatter` syntax,
  *                   such as `%d` to insert an integer number. Alternatively, if the file name does
  *                   not contain a `%` character but a digit or a sequence of digits, those digits
  *                   will be replaced by `%d` to produce a valid template.
  *                   Therefore, if the template is `foo-123.jpg` and the indices contain `4` and `5`,
  *                   then the UGen will read the images `foo-4` and `foo-5` (the placeholder `123` is
  *                   irrelevant).
  */
final case class ImageFileSeqIn(template: URI, numChannels: Int, indices: GE) extends UGenSource.MultiOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(indices.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
    val t = Util.mkTemplate(template)
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
      // XXX TODO --- obviously the template does not capture the specs of the individual files :-(
      adjuncts = Adjunct.FileIn(t) :: Adjunct.Int(numChannels) :: Nil,
      isIndividual = true, hasSideEffect = true)
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(indices) = args
    val t = Util.mkTemplate(template)
    stream.ImageFileSeqIn(template = t, numChannels = numChannels, indices = indices.toInt)
  }
}