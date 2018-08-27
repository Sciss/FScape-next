/*
 *  GeomSeq.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.{BufD, BufL, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that produces a geometric sequence of values.
  * If both `start` and `grow` are of integer type (`Int` or `Long`),
  * this produces an integer output, otherwise it produces a floating point output.
  *
  * E.g. `GeomSeq(start = 1, grow = 2)` will produce a series `1, 2, 4, 8, ...`.
  *
  * @param start    the first output element
  * @param grow     the multiplicative factor by which each successive element will grow
  * @param length   the number of elements to output
  */
final case class GeomSeq(start: GE = 1, grow: GE = 2, length: GE = Long.MaxValue)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(start.expand, grow.expand, length.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(start, grow, length) = args

    if ((start.isInt || start.isLong) && (grow.isInt || grow.isLong)) {
      stream.GeomSeq[Long  , BufL](start = start.toLong  , grow = grow.toLong  , length = length.toLong)
    } else {
      stream.GeomSeq[Double, BufD](start = start.toDouble, grow = grow.toDouble, length = length.toLong)
    }
  }
}