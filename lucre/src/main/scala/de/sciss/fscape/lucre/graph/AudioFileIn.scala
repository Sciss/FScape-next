/*
 *  AudioFileIn.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package graph

import de.sciss.file.File
import de.sciss.fscape
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.AudioCue

object AudioFileIn {
  final case class NumFrames(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$NumFrames"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cue = AudioFileIn.getCue(key, b)
      cue.spec.numFrames - cue.fileOffset
    }
  }

  final case class SampleRate(key: String) extends GE.Lazy {
    override def productPrefix = s"AudioFileIn$$SampleRate"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val cue = AudioFileIn.getCue(key, b)
      cue.spec.sampleRate
    }
  }

  private def getCue(key: String, b: UGenGraph.Builder): AudioCue = {
    val ub  = UGenGraphBuilder.get(b)
    val res = ub.requestAttribute(key).fold[AudioCue](sys.error(s"AudioFileIn missing attribute $key")) {
      case a: AudioCue => a
      case f: File =>
        val spec = AudioFile.readSpec(f)
        AudioCue(f, spec, 0L, 1.0)
      case other => sys.error(s"AudioFileIn - requires AudioCue or Artifact value, found $other")
    }
    res
  }
}
final case class AudioFileIn(key: String) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val cue = AudioFileIn.getCue(key, b)
    val in0 = fscape.graph.AudioFileIn(file = cue.artifact, numChannels = cue.spec.numChannels)
    val off = cue.fileOffset
    val in1 = if (off <= 0) in0 else in0.drop(off)
    val in  = if (cue.gain == 1.0) in1 else in1 * cue.gain
    in
  }

  def numFrames : GE = AudioFileIn.NumFrames (key)
  def sampleRate: GE = AudioFileIn.SampleRate(key)
}