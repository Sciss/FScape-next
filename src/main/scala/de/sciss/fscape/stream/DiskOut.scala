/*
 *  DiskOut.scala
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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.GraphDSL
import de.sciss.file.File
import de.sciss.synth.io.AudioFileSpec

object DiskOut {
  def apply(file: File, spec: AudioFileSpec, in: Outlet[BufD])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Unit = {
    val sink = new AudioFileSink(file, spec)
    import GraphDSL.Implicits._
    in ~> sink
  }
}