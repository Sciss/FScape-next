/*
 *  Impulse.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

/** Impulse (repeated dirac) generator.
  * For a single impulse that is never repeated,
  * use zero.
  *
  * @param freqN  normalized frequency (reciprocal of frame period)
  * @param phase  phase offset in cycles (0 to 1).
  */
final case class Impulse(freqN: GE, phase: GE = 0.0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(freqN.expand, phase.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(freqN, phase) = args
    stream.Impulse(freqN = freqN.toDouble, phase = phase.toDouble)
  }
}