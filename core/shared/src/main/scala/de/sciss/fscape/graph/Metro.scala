/*
 *  Metro.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object Metro extends ProductReader[Metro] {
  override def read(in: RefMapIn, key: String, arity: Int): Metro = {
    require (arity == 2)
    val _period = in.readGE()
    val _phase  = in.readGE()
    new Metro(_period, _phase)
  }
}
/** Metronome (repeated dirac) generator.
  * For a single impulse that is never repeated,
  * use a period of zero. Unlike `Impulse` which
  * uses a frequency and generates fractional phases
  * prone to floating point noise, this is UGen is
  * useful for exact sample frame spacing. Unlike `Impulse`,
  * the phase cannot be modulated.
  *
  * @param period number of frames between impulses. Zero is short-hand
  *               for an infinitely long period. One value is read per output period.
  *
  * @param phase  phase offset in frames. One value is read per output period.
  */
final case class Metro(period: GE, phase: GE = 0L) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(period.expand, phase.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(period, phase) = args
    stream.Metro(period = period.toLong, phase = phase.toLong)
  }
}