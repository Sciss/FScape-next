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

package de.sciss.fscape
package lucre
package graph

import de.sciss.file.File
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream.{BufD, BufL, StreamIn, StreamOut, Builder => SBuilder}
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.AudioCue

import scala.util.{Failure, Success, Try}

object AudioFileIn {
  final case class NumFrames(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$NumFrames"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cueTr = AudioFileIn.getCue(key, b)
      val nTr   = cueTr.map(_.numFrames)
      NumFramesWithValue(nTr)
    }
  }

  final case class NumFramesWithValue(tr: Try[Long]) extends UGenSource.SingleOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
      val adjuncts = tr.toOption.fold(List.empty[Adjunct])(Adjunct.Long(_) :: Nil)  // Try#fold requires Scala 2.12
      UGen.SingleOut(this, args, adjuncts = adjuncts)
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): StreamOut = {
      stream.TryConstant[Long, BufL](tr.map(BufL(_)))
    }

    override def productPrefix: String = s"AudioFileIn$$NumFramesWithValue"
  }

  final case class SampleRate(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$SampleRate"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cueTr = AudioFileIn.getCue(key, b)
      val srTr  = cueTr.map(_.sampleRate)
      SampleRateWithValue(srTr)
    }
  }

  final case class SampleRateWithValue(tr: Try[Double]) extends UGenSource.SingleOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
      val adjuncts = tr.toOption.fold(List.empty[Adjunct])(Adjunct.Double(_) :: Nil)   // Try#fold requires Scala 2.12
      UGen.SingleOut(this, args, adjuncts = adjuncts)
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): StreamOut = {
      stream.TryConstant[Double, BufD](tr.map(BufD(_)))
    }

    override def productPrefix: String = s"AudioFileIn$$SampleRateWithValue"
  }

  private def getCue(key: String, b: UGenGraph.Builder): Try[AudioCue] = {
    val ub  = UGenGraphBuilder.get(b)
    val res = Try {
      ub.requestInput(Input.Attribute(key)).peer.fold[AudioCue](sys.error(s"AudioFileIn missing attribute $key")) {
        case a: AudioCue => a
        case f: File =>
          val spec = AudioFile.readSpec(f)
          AudioCue(f, spec, 0L, 1.0)
        case other => sys.error(s"AudioFileIn - requires AudioCue or Artifact value, found $other")
      }
    }
    res
  }

  final case class WithCue(cueTr: Try[AudioCue]) extends UGenSource.MultiOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
      val (adjuncts, numCh) = cueTr match {
        case Success(cue) =>
          val _adjuncts = Adjunct.FileIn(cue.artifact) :: Adjunct.AudioFileSpec(cue.spec) ::
            Adjunct.Long(cue.offset) :: Adjunct.Double(cue.gain) :: Nil
          (_adjuncts, cue.numChannels)

        case Failure(_) => (Nil, 1)
      }
      UGen.MultiOut(this, args, numOutputs = numCh, adjuncts = adjuncts)
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): Vec[StreamOut] = {
      val numCh = cueTr.toOption.fold(1)(_.spec.numChannels)  // Try#fold requires Scala 2.12
      stream.AudioFileIn(cueTr, numChannels = numCh)
    }

    override def productPrefix: String = s"AudioFileIn$$WithCue"
  }
}
final case class AudioFileIn(key: String) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val cueTr = AudioFileIn.getCue(key, b)
    AudioFileIn.WithCue(cueTr)
  }

  def numFrames : GE = AudioFileIn.NumFrames (key)
  def sampleRate: GE = AudioFileIn.SampleRate(key)
}