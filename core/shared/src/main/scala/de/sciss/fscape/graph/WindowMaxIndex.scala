/*
 *  WindowMaxIndex.scala
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

/** A UGen that determines for each input window the index of the maximum element.
  * It outputs one integer value per window; if multiple elements have the same
  * value, the index of the first element is reported (notably zero if the window
  * contains only identical elements).
  *
  * @param in     the input signal.
  * @param size   the window size.
  */
final case class WindowMaxIndex(in: GE, size: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size) = args
    import in.tpe
    stream.WindowMaxIndex[in.A, in.Buf](in = in.toElem, size = size.toInt)
  }
}
