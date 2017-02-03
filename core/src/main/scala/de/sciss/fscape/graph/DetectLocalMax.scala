/*
 *  DetectLocalMax.scala
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

/** A UGen that outputs triggers for local maxima within
  * a sliding window. If multiple local maxima occur
  * within the current window, only the one with
  * the largest value will cause the trigger.
  *
  * By definition, the first and last value in the input stream
  * cannot qualify for local maxima.
  *
  * @param in     the signal to analyze for local maxima
  * @param size   the sliding window size. Each two
  *               emitted triggers are spaced apart at least
  *               by `size` frames.
  */
final case class DetectLocalMax(in: GE, size: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size) = args
    val inE = in.toElem
    import in.tpe
    stream.DetectLocalMax[in.A, in.Buf](in = inE, size = size.toInt)
  }
}