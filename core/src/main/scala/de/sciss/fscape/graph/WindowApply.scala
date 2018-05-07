/*
 *  WindowApply.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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

/** A UGen that determines for each input window the first index where a predicate holds.
  * It outputs one integer value per window; if the predicate does not hold across the entire
  * window, the index will be `-1`.
  *
  * @param in     the window'ed signal to index
  * @param size   the window size.
  * @param index  the zero-based index into each window. One value per window is polled.
  * @param wrap   if non-zero, wraps indices around the window boundaries, otherwise clips.
  */
final case class WindowApply(in: GE, size: GE, index: GE = 0, wrap: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, index.expand, wrap.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, index, wrap) = args
    mkStream[in.A, in.Buf](in = in, size = size.toInt, index = index.toInt, wrap = wrap.toInt)
  }

  private def mkStream[A, BufA >: Null <: BufElem[A]](in: StreamInElem[A, BufA], size: OutI, index: OutI, wrap: OutI)
                                                     (implicit b: Builder): StreamOut = {
    import in.{tpe => inTpe}  // IntelliJ doesn't get it
    val out: Outlet[BufA] = stream.WindowApply[A, BufA](in = in.toElem, size = size, index = index, wrap = wrap)
    inTpe.mkStreamOut(out) // IntelliJ doesn't get it
  }
}
