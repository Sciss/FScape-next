/*
 *  Line.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A line segment generating UGen. The UGen terminates
  * when the segment has reached the end.
  *
  * A line can be used to count integers (in the lower
  * ranges, where floating point noise is not yet relevant),
  * e.g. `Line(a, b, b - a + 1)` counts from `a` to
  * `b` (inclusive).
  *
  * @param start  starting value
  * @param end    ending value
  * @param length length of the segment in sample frames
  */
final case class Line(start: GE, end: GE, length: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(start.expand, end.expand, length.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(start, end, length) = args
    stream.Line(start = start.toDouble, end = end.toDouble, length = length.toLong)
  }
}