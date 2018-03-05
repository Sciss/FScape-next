/*
 *  DCT_II.scala
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

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen for type II discrete cosine transform.
  *
  * @param in         input signal
  * @param size       input signal window size
  * @param numCoeffs  number of coefficients output
  * @param zero       if zero (default), the zero'th coefficient is
  *                   _skipped_ in the output, if
  *                   non-zero, the zero'th coefficient is
  *                   included. In any case, the output
  *                   window size is `numCoeffs`.
  */
final case class DCT_II(in: GE, size: GE, numCoeffs: GE, zero: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, numCoeffs.expand, zero.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, numCoeffs, zero) = args
    stream.DCT_II(in = in.toDouble, size = size.toInt, numCoeffs = numCoeffs.toInt, zero = zero.toInt)
  }
}
