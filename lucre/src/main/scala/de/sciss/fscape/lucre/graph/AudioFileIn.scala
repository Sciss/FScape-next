/*
 *  AudioFileIn.scala
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
package lucre
package graph

import de.sciss.file.File
import de.sciss.fscape
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.AudioCue

final case class AudioFileIn(key: String) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val ub  = UGenGraphBuilder.get(b)
    val cue = ub.requestAttribute(key).fold[AudioCue](sys.error(s"Missing Attribute $key")) {
      case a: AudioCue => a
      case f: File =>
        val spec = AudioFile.readSpec(f)
        AudioCue(f, spec, 0L, 1.0)
      case other => sys.error(s"$this - requires AudioCue or Artifact value, found $other")
    }
    val in0 = fscape.graph.AudioFileIn(file = cue.artifact, numChannels = cue.spec.numChannels)
    val in1 = if (cue.offset <= 0) in0 else in0.drop(cue.offset)
    val in  = if (cue.gain == 1.0) in1 else in1 * cue.gain
    in
  }
}