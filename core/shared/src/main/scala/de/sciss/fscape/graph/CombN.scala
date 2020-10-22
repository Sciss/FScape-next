/*
 *  CombN.scala
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

/** A comb filter delay line UGen with no interpolation.
  *
  * @param  in        the signal to filter
  * @param  maxLength the maximum delay time in sample frames. this is only read once
  *                   and constrains the values of `length`
  * @param  length    the delay time in sample frames. The value can be obtained from
  *                   the delay time in seconds by multiplication with the sampling rate.
  * @param  decay     the feedback's -60 dB decay time in sample frames. The value can be obtained from
  *                   the decay time in seconds by multiplication with the sampling rate.
  *                   Negative numbers are allowed and mean that the feedback signal is
  *                   phase inverted.
  */
final case class CombN(in: GE, maxLength: GE, length: GE, decay: GE = 44100)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, maxLength.expand, length.expand, decay.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, maxLength, length, decay) = args
    stream.CombN(in = in.toDouble, maxLength = maxLength.toInt, length = length.toInt, decay = decay.toDouble)
  }
}