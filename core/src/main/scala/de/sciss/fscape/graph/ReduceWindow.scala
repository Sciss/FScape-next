/*
 *  ReduceWindow.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object ReduceWindow {
  import BinaryOp.{And, Or, Xor, Max, Min, Plus, Times}
  def +  (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Plus  .id)
  def *  (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Times .id)
  def min(in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Min   .id)
  def max(in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Max   .id)
  def &  (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = And.id)
  def |  (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Or .id)
  def ^  (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Xor.id)
}

/** A UGen that reduces all elements in each window to single values, for example
  * by calculating the sum or product.
  */
final case class ReduceWindow(in: GE, size: GE, op: Int) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UGenSource.unwrap(this, Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args, adjuncts = UGen.Adjunct.Int(op) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size) = args
    stream.ReduceWindow(in = in.toDouble, size = size.toInt, op = BinaryOp.Op(op))
  }
}
