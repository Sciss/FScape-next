/*
 *  Impulse.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** Impulse (repeated dirac) generator.
  * For a single impulse that is never repeated,
  * use zero.
  *
  * @param period number of frames between impulses
  * @param phase  phase offset in cycles (0 to 1).
  */
final case class ImpulseFOO(period: GE, phase: GE = 0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(period.expand, phase.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(period, phase) = args
    stream.ImpulseFOO(period = period.toDouble, phase = phase.toDouble)
  }
}