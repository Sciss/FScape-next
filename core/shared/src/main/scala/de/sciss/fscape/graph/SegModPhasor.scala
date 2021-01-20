/*
 *  SegModPhasor.scala
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

object SegModPhasor extends ProductReader[SegModPhasor] {
  override def read(in: RefMapIn, key: String, arity: Int): SegModPhasor = {
    require (arity == 2)
    val _freqN  = in.readGE()
    val _phase  = in.readGE()
    new SegModPhasor(_freqN, _phase)
  }
}
/** A phasor UGen that takes new frequency values at the beginning of each cycle.
  * It can be used to implement the 'segmod' program of Döbereiner and Lorenz.
  * In contrast to `LFSaw` which continuously reads frequency values, its
  * output values go from zero to one, and the phase argument is only used
  * internally (the output signal always starts at zero).
  *
  * To turn this, for example, into a sine oscillator, you can use
  * `(SegModPhasor(...) * 2 * math.Pi).sin`.
  *
  * '''Warning:''' passing in zero frequencies will halt the process. XXX TODO
  *
  * @param freqN  normalized frequency (f/sr). One value per output cycle is read.
  *               Also note that the UGen terminates when the `freqN` signal ends.
  *               Thus, to use a constant frequency, wrap it in a `DC` or `DC(...).take`.
  * @param phase  phase offset in radians. The phase offset is only used internally to
  *               determine when to change the frequency. For example, with a phase of
  *               `0.25`, when the output phase reaches 0.25, the next frequency value
  *               is read. With the default phase of 0.0, a frequency value is read
  *               after one complete cycle (output reaches 1.0).
  */
final case class SegModPhasor(freqN: GE, phase: GE = 0.0) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(freqN.expand, phase.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(freqN, phase) = args
    stream.SegModPhasor(freqN = freqN.toDouble, phase = phase.toDouble)
  }
}