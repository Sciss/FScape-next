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

import akka.stream.scaladsl.GraphDSL
import de.sciss.file.File
import de.sciss.fscape.stream.impl.AudioFileSink
import de.sciss.synth.io.AudioFileSpec

import scala.collection.immutable.{Seq => ISeq}

object DiskOut {
  def apply(file: File, spec: AudioFileSpec, in: ISeq[OutD])(implicit b: GBuilder, ctrl: Control): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new AudioFileSink(file, spec)
    import GraphDSL.Implicits._
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      output ~> input
    }
  }
}