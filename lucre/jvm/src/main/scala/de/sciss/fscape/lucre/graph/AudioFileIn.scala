/*
 *  AudioFileIn.scala
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

package de.sciss.fscape.lucre
package graph

import java.net.URI

import de.sciss.audiofile.AudioFile
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.graph.{ConstantD, ConstantL}
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.lucre.graph.impl.FutureConstant
import de.sciss.fscape.stream.{BufL, StreamIn, StreamOut, Builder => SBuilder}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource}
import de.sciss.lucre.Artifact
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.proc.AudioCue

object AudioFileIn {
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

//  final case class NumFramesWithValue(tr: Try[Long]) extends UGenSource.SingleOut {
//
//    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
//      makeUGen(Vector.empty)
//
//    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
//      val adjuncts = tr.toOption.fold(List.empty[Adjunct])(Adjunct.Long(_) :: Nil)  // Try#fold requires Scala 2.12
//      UGen.SingleOut(this, args, adjuncts = adjuncts)
//    }
//
//    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): StreamOut = {
//      stream.TryConstant[Long, BufL](tr.map(BufL(_)))
//    }
//
//    override def productPrefix: String = s"AudioFileIn$$NumFramesWithValue"
//  }

  final case class SampleRate(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$SampleRate"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cueTr = AudioFileIn.getCue(key, b)
      cueTr match {
        case Left (cue) => ConstantD(cue.sampleRate)
        case Right(uri) => ???
      }
    }
  }

//  final case class SampleRateWithValue(tr: Try[Double]) extends UGenSource.SingleOut {
//
//    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
//      makeUGen(Vector.empty)
//
//    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
//      val adjuncts = tr.toOption.fold(List.empty[Adjunct])(Adjunct.Double(_) :: Nil)   // Try#fold requires Scala 2.12
//      UGen.SingleOut(this, args, adjuncts = adjuncts)
//    }
//
//    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): StreamOut = {
//      stream.TryConstant[Double, BufD](tr.map(BufD(_)))
//    }
//
//    override def productPrefix: String = s"AudioFileIn$$SampleRateWithValue"
//  }

//  private sealed trait CueOrArtifactOption
//  private final case class  IsCue      (peer: AudioCue       ) extends CueOrArtifactOption
//  private final case class  IsArtifact (peer: Artifact.Value ) extends CueOrArtifactOption
//  private final case object NotCueOrArtifact extends CueOrArtifactOption

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
        AudioFileIn.WithCue(uri, offset = 0L, gain = 1.0, numChannels = 1)
    }
  }

  def numFrames : GE = AudioFileIn.NumFrames (key)
  def sampleRate: GE = AudioFileIn.SampleRate(key)
}