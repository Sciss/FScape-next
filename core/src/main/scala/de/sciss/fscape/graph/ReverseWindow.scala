/*
 *  ReverseWindow.scala
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

/** Reverses the (clumped) elements of input windows.
  *
  * E.g. `ReverseWindow(ArithmSeq(1, 1, 9), 3)` gives `7,8,9, 4,5,6, 1,2,3`.
  * The behavior when `size % clump != 0` is to clump "from both sides" of
  * the window, e.g. `ReverseWindow(ArithmSeq(1, 1, 9), 4)` gives `6,7,8,9, 5, 1,2,3,4`.
  *
  * @param  in      the window'ed signal to reverse
  * @param  size    the window size
  * @param  clump   the grouping size of window elements
  */
final case class ReverseWindow(in: GE, size: GE, clump: GE = 1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, clump.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, clump) = args
    import in.tpe
    val out = stream.ReverseWindow[in.A, in.Buf](in = in.toElem, size = size.toInt, clump = clump.toInt)
    tpe.mkStreamOut(out)
  }
}