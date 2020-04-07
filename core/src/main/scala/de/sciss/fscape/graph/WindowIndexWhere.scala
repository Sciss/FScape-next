/*
 *  WindowIndexWhere.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that determines for each input window the first index where a predicate holds.
  * It outputs one integer value per window; if the predicate does not hold across the entire
  * window or if the window size is zero, the index will be `-1`.
  *
  * @param p      a predicate to detect the index
  * @param size   the window size.
  */
final case class WindowIndexWhere(p: GE, size: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(p.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(p, size) = args
    stream.WindowIndexWhere(p = p.toInt, size = size.toInt)
  }
}
