/*
 *  AudioFileInPlatform.scala
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

package de.sciss.fscape.lucre.graph

import java.io.File
import java.net.URI

import de.sciss.audiofile.AudioFile

trait AudioFileInPlatform {
  private[graph] def mkCue(uri: URI): AudioFileIn.WithCue = {
    val f     = new File(uri)
    val spec  = AudioFile.readSpec(f)
    AudioFileIn.WithCue(uri, offset = 0L, gain = 1.0, numChannels = spec.numChannels)
  }
}
