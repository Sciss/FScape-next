/*
 *  OverlapAdd.scala
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
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that performs overlap-and-add operation on a stream of input windows.
  * The `size` and `step` parameters are demand-rate, polled once per (input) window.
  *
  * @param in     the non-overlapped input
  * @param size   the window size in the input
  * @param step   the step between successive windows in the output.
  *               when smaller than `size`, the overlapping portions are summed together.
  *
  * @see [[Sliding]]
  */
final case class OverlapAdd(in: GE, size: GE, step: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, step.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, step) = args
    stream.OverlapAdd(in = in.toDouble, size = size.toInt, step = step.toInt)
  }
}