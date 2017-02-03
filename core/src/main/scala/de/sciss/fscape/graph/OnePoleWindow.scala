/*
 *  OnePoleWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
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

/** A one pole (IIR) filter UGen applied to windowed data. Implements the formula :
  * {{{
  * out(i) = ((1 - abs(coef)) * in(i)) + (coef * out(i-1))
  * }}}
  *
  * This filter runs in parallel for all frames of the window (or matrix).
  * That is, in the above formula `out` is replaced by each window element, and
  * `i` is the window count.
  *
  * @param in   input signal to be processed
  * @param size window size
  * @param coef feedback coefficient. Should be between -1 and +1
  */
final case class OnePoleWindow(in: GE, size: GE, coef: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, coef.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, coef) = args
    stream.OnePoleWindow(in = in.toDouble, size = size.toInt, coef = coef.toDouble)
  }
}