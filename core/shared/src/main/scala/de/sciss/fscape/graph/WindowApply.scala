/*
 *  WindowApply.scala
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

import akka.stream.Outlet
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{BufElem, Builder, OutI, StreamIn, StreamInElem, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that extracts for each input window the element at a given index.
  *
  * For example, the first element per window can be extracted with `index = 0`,
  * and the last element per window can be extracted with `index = -1, mode = 1` (wrap).
  *
  * If the input `in` terminates before a window of `size` is full, it will be padded
  * with zeroes.
  *
  * @param in     the window'ed signal to index
  * @param size   the window size.
  * @param index  the zero-based index into each window. One value per window is polled.
  * @param mode   wrap mode. `0` clips indices, `1` wraps them around, `2` folds them, `3` outputs
  *               zeroes when index is out of bounds.
  */
final case class WindowApply(in: GE, size: GE, index: GE = 0, mode: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, index.expand, mode.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, index, mode) = args
    mkStream[in.A, in.Buf](in = in, size = size.toInt, index = index.toInt, mode = mode.toInt)
  }

  private def mkStream[A, BufA >: Null <: BufElem[A]](in: StreamInElem[A, BufA], size: OutI, index: OutI, mode: OutI)
                                                     (implicit b: Builder): StreamOut = {
    import in.{tpe => inTpe}
    val out: Outlet[BufA] = stream.WindowApply[A, BufA](in = in.toElem, size = size, index = index, mode = mode)
    inTpe.mkStreamOut(out)
  }
}
