/*
 *  Histogram.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object Histogram extends ProductReader[Histogram] {
  override def read(in: RefMapIn, key: String, arity: Int): Histogram = {
    require (arity == 6)
    val _in     = in.readGE()
    val _bins   = in.readGE()
    val _lo     = in.readGE()
    val _hi     = in.readGE()
    val _mode   = in.readGE()
    val _reset  = in.readGE()
    new Histogram(_in, _bins, _lo, _hi, _mode, _reset)
  }
}
/** A UGen that calculates running histogram of an input signal with
  * given boundaries and bin-size. The bins are divided linearly,
  * if another mapping (e.g. exponential) is needed, it must be pre-applied to the input signal.
  *
  * '''Note:''' currently parameter modulation (`bin`, `lo`, `hi`, `mode`, `reset`) is not
  * working correctly.
  *
  * @param in       the input signal
  * @param bins     the number of bins. this is read at initialization time only or when `reset`
  *                 fires
  * @param lo       the lowest bin boundary. input values below this value are clipped.
  *                 this value may be updated (although that is seldom useful).
  * @param hi       the highest bin boundary. input values above this value are clipped.
  *                 this value may be updated (although that is seldom useful).
  * @param mode     if `0` (default), outputs only after `in` has finished, if `1` outputs
  *                 the entire histogram for every input sample.
  * @param reset    when greater than zero, resets the count.
  *
  * @see  [[NormalizeWindow]]
  */
final case class Histogram(in: GE, bins: GE, lo: GE = 0.0, hi: GE = 1.0, mode: GE = 0, reset: GE = 0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, bins.expand, lo.expand, hi.expand, mode.expand, reset.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, bins, lo, hi, mode, reset) = args
    stream.Histogram(in = in.toDouble, bins = bins.toInt, lo = lo.toDouble, hi = hi.toDouble,
      mode = mode.toInt, reset = reset.toInt)
  }
}