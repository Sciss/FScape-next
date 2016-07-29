/*
 *  ImageFileSeqIn.scala
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
package graph

import de.sciss.file.File
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

final case class ImageFileSeqIn(template: File, numChannels: Int, indices: GE) extends UGenSource.MultiOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(indices.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels, rest = template,
      isIndividual = true, hasSideEffect = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(indices) = args
    stream.ImageFileSeqIn(template = template, numChannels = numChannels, indices = indices.toInt)
  }
}