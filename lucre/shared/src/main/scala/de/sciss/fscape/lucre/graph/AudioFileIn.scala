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

package de.sciss.fscape.lucre
package graph

import java.net.URI

import de.sciss.audiofile.AudioFile
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.graph.{ConstantD, ConstantL}
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.lucre.graph.impl.FutureConstant
import de.sciss.fscape.stream.{BufD, BufL, StreamIn, StreamOut, Builder => SBuilder}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource}
import de.sciss.lucre.Artifact
import de.sciss.synth.UGenSource.Vec
import de.sciss.proc.AudioCue

object AudioFileIn extends AudioFileInPlatform {
  final case class NumFrames(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$NumFrames"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cueTr = AudioFileIn.getCue(key, b)
      cueTr match {
        case Left (cue) => ConstantL(cue.numFrames)
        case Right(uri) => FutureConstant[Long, BufL](Adjunct.FileIn(uri), { ctrl =>
          import ctrl.config.executionContext
          AudioFile.readSpecAsync(uri).map(_.numFrames)
        })
      }
    }
  }

  final case class SampleRate(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$SampleRate"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cueTr = AudioFileIn.getCue(key, b)
      cueTr match {
        case Left (cue) => ConstantD(cue.sampleRate)
        case Right(uri) => FutureConstant[Double, BufD](Adjunct.FileIn(uri), { ctrl =>
          import ctrl.config.executionContext
          AudioFile.readSpecAsync(uri).map(_.sampleRate)
        })
      }
    }
  }

  private def getCue(key: String, b: UGenGraph.Builder): Either[AudioCue, Artifact.Value] = {
    val ub  = UGenGraphBuilder.get(b)
    val v = ub.requestInput(Input.Attribute(key)).peer.getOrElse(sys.error(s"AudioFileIn missing attribute $key"))
    v match {
      case a: AudioCue        => Left(a)
      case f: Artifact.Value  => Right(f)
      case other => sys.error(s"AudioFileIn - requires AudioCue or Artifact value, found $other")
    }
  }

  final case class WithCue(uri: URI, offset: Long, gain: Double, numChannels: Int)
    extends UGenSource.MultiOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
      val adjuncts =
        Adjunct.FileIn(uri) :: /*Adjunct.AudioFileSpec(cue.spec) ::*/
            Adjunct.Long(offset) :: Adjunct.Double(gain) :: Nil

      UGen.MultiOut(this, args, numOutputs = numChannels, adjuncts = adjuncts)
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): Vec[StreamOut] = {
      stream.AudioFileIn(uri = uri, offset = offset, gain = gain, numChannels = numChannels)
    }

    override def productPrefix: String = s"AudioFileIn$$WithCue"
  }
}
final case class AudioFileIn(key: String) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val cueTr = AudioFileIn.getCue(key, b)
    cueTr match {
      case Left (cue) =>
        AudioFileIn.WithCue(cue.artifact, cue.offset, cue.gain, cue.spec.numChannels)
      case Right(uri) =>
        // XXX TODO -- we do have to find a way to determine the number of channels
        // before expanding the UGen
        // - could be synchronous on JVM and yet unsupported on JS
        // - should have an asynchronous `prepare` stage like AuralProc
//        AudioFileIn.WithCue(uri, offset = 0L, gain = 1.0, numChannels = 1)
        AudioFileIn.mkCue(uri)
    }
  }

  def numFrames : GE = AudioFileIn.NumFrames (key)
  def sampleRate: GE = AudioFileIn.SampleRate(key)
}