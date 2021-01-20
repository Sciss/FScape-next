/*
 *  OnePole.scala
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

object OnePole extends ProductReader[OnePole] {
  override def read(in: RefMapIn, key: String, arity: Int): OnePole = {
    require (arity == 2)
    val _in   = in.readGE()
    val _coef = in.readGE()
    new OnePole(_in, _coef)
  }
}
/** A one pole (IIR) filter UGen. Implements the formula :
  * {{{
  * out(i) = ((1 - abs(coef)) * in(i)) + (coef * out(i-1))
  * }}}
  *
  * @param in   input signal to be processed
  * @param coef feedback coefficient. Should be between -1 and +1
  */
final case class OnePole(in: GE, coef: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, coef.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, coef) = args
    stream.OnePole(in = in.toDouble, coef = coef.toDouble)
  }
}