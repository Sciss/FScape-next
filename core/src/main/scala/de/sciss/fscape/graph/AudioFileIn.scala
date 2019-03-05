/*
 *  AudioFileIn.scala
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

import de.sciss.file.File
import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

final case class AudioFileIn(file: File, numChannels: Int) extends UGenSource.MultiOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = makeUGen(Vector.empty)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
      aux = Aux.FileIn(file) :: Aux.Int(numChannels) :: Nil,
      isIndividual = true, hasSideEffect = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] =
    stream.AudioFileIn(file = file, numChannels = numChannels)
}