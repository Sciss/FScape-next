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

package de.sciss.fscape
package graph

import de.sciss.file.File
import de.sciss.fscape.stream.StreamIn
import de.sciss.synth.io.AudioFileSpec

import scala.collection.immutable.{IndexedSeq => Vec}

final case class DiskOut(file: File, spec: AudioFileSpec, in: GE) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = unwrap(in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args, rest = file, isIndividual = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    stream.DiskOut(file = file, spec = spec, in = args.map(_.toDouble))
  }
}