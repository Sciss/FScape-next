/*
 *  AudioFileIn.scala
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
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object AudioFileIn extends ProductReader[AudioFileIn] {
  override def read(in: RefMapIn, key: String, arity: Int): AudioFileIn = {
    require (arity == 2)
    val _file         = in.readURI()
    val _numChannels  = in.readInt()
    new AudioFileIn(_file, _numChannels)
  }
}
final case class AudioFileIn(file: URI, numChannels: Int) extends UGenSource.MultiOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = makeUGen(Vector.empty)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
      adjuncts = Adjunct.FileIn(file) :: Adjunct.Int(numChannels) :: Nil,
      isIndividual = true, hasSideEffect = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] =
    stream.AudioFileIn(uri = file, numChannels = numChannels)
}