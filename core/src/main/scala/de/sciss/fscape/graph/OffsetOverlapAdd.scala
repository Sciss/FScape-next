/*
 *  OffsetOverlapAdd.scala
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
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** Overlapping window summation with offset (fuzziness) that can be modulated. */
final case class OffsetOverlapAdd(in: GE, size: GE, step: GE, offset: GE, minOffset: GE)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, step.expand, offset.expand, minOffset.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, step, offset, minOffset) = args
    stream.OffsetOverlapAdd(in = in.toDouble, size = size.toInt, step = step.toInt,
      offset = offset.toInt, minOffset = minOffset.toInt)
  }
}