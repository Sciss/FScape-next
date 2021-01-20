/*
 *  LeakDC.scala
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

object LeakDC extends ProductReader[LeakDC] {
  override def read(in: RefMapIn, key: String, arity: Int): LeakDC = {
    require (arity == 2)
    val _in     = in.readGE()
    val _coeff  = in.readGE()
    new LeakDC(_in, _coeff)
  }
}
/** A filter UGen to remove very low frequency content DC offset.
  *
  * This is a one-pole highpass filter implementing the formula
  * {{{
  * y[n] = x[n] - x[n-1] + coeff * y[n-1]
  * }}}
  *
  *
  * @param in               input signal to be filtered
  * @param coeff            the leak coefficient determines the filter strength.
  *                         the value must be between zero and one (exclusive) for
  *                         the filter to remain stable. values closer to one
  *                         produce less bass attenuation.
  */
final case class LeakDC(in: GE, coeff: GE = 0.995) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    import Ops._
    Biquad(in, b0 = 1.0, b1 = -1.0, a1 = -coeff)
  }
}
