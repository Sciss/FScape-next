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

import scala.collection.immutable.IndexedSeq

case class DiskIn(file: File, numChannels: Int) extends UGenSource.SingleOut {
  protected def makeUGen(args: IndexedSeq[UGenIn]): UGenInLike = ???

  protected def makeUGens: UGenInLike = ???
}