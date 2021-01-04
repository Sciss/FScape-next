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

import java.net.URI

import de.sciss.fscape.Log

trait AudioFileInPlatform {
  private[graph] def mkCue(uri: URI): AudioFileIn.WithCue = {
    Log.graph.warn("AudioFileIn: cannot determinate number of channels on Scala.js. Assuming monophonic.")
    AudioFileIn.WithCue(uri, offset = 0L, gain = 1.0, numChannels = 1)
  }
}
