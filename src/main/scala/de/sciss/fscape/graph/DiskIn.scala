/*
 *  DiskIn.scala
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
import de.sciss.fscape.stream.{Control, GBuilder, StreamIn}

import scala.collection.immutable.{IndexedSeq => Vec}

case class DiskIn(file: File, numChannels: Int) extends UGenSource.MultiOut {
  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
    UGen.MultiOut(this, inputs = args, numOutputs = numChannels, isIndividual = true, hasSideEffect = true)
  }

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = unwrap(Vector.empty)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamIn] =
    stream.DiskIn(file = file, numChannels = numChannels)
}