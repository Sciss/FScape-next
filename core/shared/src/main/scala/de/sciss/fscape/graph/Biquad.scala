/*
 *  Biquad.scala
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

object Biquad extends ProductReader[Biquad] {
  override def read(in: RefMapIn, key: String, arity: Int): Biquad = {
    require (arity == 6)
    val _in = in.readGE()
    val _b0 = in.readGE()
    val _b1 = in.readGE()
    val _b2 = in.readGE()
    val _a1 = in.readGE()
    val _a2 = in.readGE()
    new Biquad(_in, _b0, _b1, _b2, _a1, _a2)
  }
}
/** A second order filter section (biquad) UGen. Filter coefficients are given directly rather than
  * calculated for you. The formula is equivalent to:
  * {{{
  * y(n) = b0 * x(n) + b1 * x(n-1) + b2 * x(n-2) - a1 * y(n-1) - a2 * y(n-2)
  * }}}
  *
  * where `x` is `in`, and `y` is the output. Note the naming and signum of the coefficients
  * differs from SuperCollider's `SOS`.
  */
final case class Biquad(in: GE, b0: GE = 0.0, b1: GE = 0.0, b2: GE = 0.0, a1: GE = 0.0, a2: GE = 0.0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, b0.expand, b1.expand, b2.expand, a1.expand, a2.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, b0, b1, b2, a1, a2) = args
    stream.Biquad(in = in.toDouble, b0 = b0.toDouble, b1 = b1.toDouble, b2 = b2.toDouble,
      a1 = a1.toDouble, a2 = a2.toDouble)
  }
}