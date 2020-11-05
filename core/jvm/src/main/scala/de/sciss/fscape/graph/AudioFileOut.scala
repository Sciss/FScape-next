/*
 *  AudioFileOut.scala
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

package de.sciss.fscape
package graph

import java.net.URI

import de.sciss.audiofile.AudioFileSpec
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that reads in an audio file. The output signal
  * is the monotonously increasing number of frames written,
  * which can be used to monitor progress or determine the
  * end of the writing process. The UGen keeps running until
  * the `in` signal ends.
  *
  * @param in     the signal to write.
  * @param file   the file to write to
  * @param spec   the spec for the audio file, including numbers of channels and sample-rate
  */
final case class AudioFileOut(in: GE, file: URI, spec: AudioFileSpec) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = unwrap(this, in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args,
      adjuncts = Adjunct.FileOut(file) :: Adjunct.AudioFileSpec(spec) :: Nil, isIndividual = true, hasSideEffect = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    stream.AudioFileOut(file = file, spec = spec, in = args.map(_.toDouble))
  }
}