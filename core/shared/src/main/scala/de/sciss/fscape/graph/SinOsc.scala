/*
 *  SinOsc.scala
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

/** Sine oscillator.
  * Note that the frequency is not in Hertz but
  * the normalized frequency
  * as we do not maintained one global sample rate.
  * For a frequency in Hertz, `freqN` would be
  * that frequency divided by the assumed sample rate.
  *
  * @param freqN  normalized frequency (f/sr).
  * @param phase  phase offset in radians
  */
final case class SinOsc(freqN: GE, phase: GE = 0.0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(freqN.expand, phase.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(freqN, phase) = args
    stream.SinOsc(freqN = freqN.toDouble, phase = phase.toDouble)
  }
}