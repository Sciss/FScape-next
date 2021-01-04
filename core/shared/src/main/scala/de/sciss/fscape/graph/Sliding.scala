/*
 *  Sliding.scala
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

/** A UGen that produces a sliding window over its input.
  *
  * When the input terminates and the last window is not full, it will be flushed
  * with its partial content. Otherwise, all windows are guaranteed to be zero-padded
  * to the window length if they had been only partially filled when the input ended.
  *
  * Unlike the `sliding` operation of Scala collections, the UGen always performs steps
  * for partial windows, e.g. `Sliding(ArithmSeq(1, length = 4), size = 3, step = 1)` will
  * produce the flat output `1, 2, 3, 2, 3, 4, 3, 4, 0, 4`, thus there are four windows,
  * the first two of which are full, the third which is full by padding, and the last is
  * partial.
  *
  * @param in   the input to be repacked into windows
  * @param size the window size. this is clipped to be at least one
  * @param step the stepping factor in the input, between windows. This clipped
  *             to be at least one. If step size is larger than window size, frames in
  *             the input are skipped.
  *
  * @see [[OverlapAdd]]
  */
final case class Sliding(in: GE, size: GE, step: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, step.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, step) = args
    import in.tpe
    val out = stream.Sliding[in.A, in.Buf](in = in.toElem, size = size.toInt, step = step.toInt)
    tpe.mkStreamOut(out)
  }
}