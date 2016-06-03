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
import de.sciss.synth.io.AudioFileSpec

import scala.collection.immutable.{IndexedSeq => Vec}

case class DiskOut(file: File, spec: AudioFileSpec, in: GE) extends UGenSource.ZeroOut {
  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = ???

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = ???
}