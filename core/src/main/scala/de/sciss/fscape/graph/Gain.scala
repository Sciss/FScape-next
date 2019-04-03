/*
 *  Gain.scala
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

/** A utility UGen that can either change the relative gain of
  * an input signal, or perform normalization (doing internal buffering in that case).
  *
  * For example, to boost the gain by 6 dB, one would use `mul = 6.0.dbAmp` and `normalized = 0`.
  * To normalize the input with a 1 dB headroom, one would use `mul = -1.0.dbAmp` and `normalized = 1`.
  *
  * @param in           the signal to be adjusted in gain.
  *                     Note that this UGen accepts a multi-channel input and will not apply
  *                     multi-channel expansion, instead processing all channels together and
  *                     outputting the adjusted multi-channel signal.
  * @param mul          the gain factor, either a direct multiplier if `normalized` is zero,
  *                     or the maximum amplitude if `normalized` is one.
  * @param normalized   zero for relative gain adjustment, one to normalize the result.
  * @param bipolar      used for normalization: whether the signal is bipolar (moving from
  *                     `-maxAmp` to `+maxAmp` or unipolar (moving between zero and `+maxAmp`).
  *                     For audio signals, this should be left at its default `1`, whereas it can
  *                     be set to `0` for image data normalization.
  */
final case class Gain(in: GE, mul: GE = 1.0, normalized: GE = 1, bipolar: GE = 1)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, mul.expand +: normalized.expand +: bipolar.expand +: in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
    val numOutputs = args.size - 3
    UGen.MultiOut(this, args, numOutputs = numOutputs)
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = ???
}
