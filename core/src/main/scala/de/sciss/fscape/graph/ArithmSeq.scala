/*
 *  ArithmSeq.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

/** A UGen that produces an arithmetic sequence of values.
  * If both `start` and `step` are of integer type (`Int` or `Long`),
  * this produces an integer output, otherwise it produces a floating point output.
  *
  * E.g. `ArithmSeq(start = 1, step = 2)` will produce a series `1, 3, 5, 7, ...`.
  *
  * @param start    the first output element
  * @param step     the amount added to each successive element
  * @param length   the number of elements to output
  */
final case class ArithmSeq(start: GE = 0, step: GE = 1, length: GE = Long.MaxValue)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(start.expand, step.expand, length.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(start, step, length) = args

    if ((start.isInt || start.isLong) && (step.isInt || step.isLong)) {
      stream.ArithmSeq[Long  , BufL](start = start.toLong  , step = step.toLong  , length = length.toLong)
    } else {
      stream.ArithmSeq[Double, BufD](start = start.toDouble, step = step.toDouble, length = length.toLong)
    }
  }
}