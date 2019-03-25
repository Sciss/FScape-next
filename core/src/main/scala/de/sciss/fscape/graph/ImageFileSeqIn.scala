/*
 *  ImageFileSeqIn.scala
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

package de.sciss.fscape
package graph

import de.sciss.file._
import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFileSeqIn {
  def formatTemplate(template: File, index: Int): File = {
    val name      = template.name.format(index)
    val f         = template.parentOption.fold(file(name))(_ / name)
    f
  }
}
final case class ImageFileSeqIn(template: File, numChannels: Int, indices: GE) extends UGenSource.MultiOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(indices.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
      // XXX TODO --- obviously the template does not capture the specs of the individual files :-(
      aux = Aux.FileIn(template) :: Aux.Int(numChannels) :: Nil,
      isIndividual = true, hasSideEffect = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(indices) = args
    stream.ImageFileSeqIn(template = template, numChannels = numChannels, indices = indices.toInt)
  }
}