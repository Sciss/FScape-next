/*
 *  Convolution.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that convolves an input signal with a fixed or changing filter kernel.
  * `kernelUpdate` is read synchronous with `in`, and while it is zero the most
  * recent kernel is reused (making it possible to use more efficient calculation
  * in the frequency domain). When `kernelUpdate` becomes `1`, a new `kernel` is polled.
  *
  * For example, if you want to update the kernel every ten sample frames, then
  * `kernelUpdate` could be given as `Metro(10).tail` or `Metro(10, 1)`. If the kernel is never updated,
  * then `kernelUpdate` could be given as constant zero. If a new kernel is provided
  * for each input sample, the value could be given as constant one.
  *
  * @param in             the signal to be filtered
  * @param kernel         the filter kernel. This is read in initially and when
  *                       `kernelUpdate` is one.
  * @param kernelLen      the filter length in sample frames. One value is polled
  *                       whenever a new kernel is required.
  * @param kernelUpdate   a gate value read synchronous with `in`, specifying whether
  *                       a new kernel is to be read in (non-zero) after the next frame, or if the previous
  *                       kernel is to be reused (zero, default).
  */
final case class Convolution(in: GE, kernel: GE, kernelLen: GE, kernelUpdate: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, kernel.expand, kernelLen.expand, kernelUpdate.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, kernel, kernelLen, kernelUpdate) = args
    stream.Convolution(in = in.toDouble, kernel = kernel.toDouble, kernelLen = kernelLen.toInt,
      kernelUpdate = kernelUpdate.toInt)
  }
}
