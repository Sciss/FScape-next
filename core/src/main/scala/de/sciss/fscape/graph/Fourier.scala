/*
 *  Fourier.scala
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

/** Disk-buffered (large) Fourier transform.
  * Output windows will have a complex size of `(size + padding).nextPowerOfTwo`
  *
  * @param in       input signal to transform. This must be complex (Re, Im interleaved)
  * @param size     the (complex) window size
  * @param padding  the (complex) zero-padding size for each window
  * @param dir      the direction is `1` for forward and `-1` for backward transform.
  *                 other numbers will do funny things.
  * @param mem      the amount of frames (chunk size) to buffer in memory. this should be
  *                 a power of two.
  */
final case class Fourier(in: GE, size: GE, padding: GE = 0, dir: GE = 1.0, mem: GE = 131072)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, padding.expand, dir.expand, mem.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, padding, dir, mem) = args
    stream.Fourier(in = in.toDouble, size = size.toLong, padding = padding.toLong, dir = dir.toDouble, mem = mem.toInt)
  }
}