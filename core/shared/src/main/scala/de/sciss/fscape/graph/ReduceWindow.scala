/*
 *  ReduceWindow.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.stream.{BufD, BufI, BufL, StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object ReduceWindow {
  import BinaryOp.{And, Or, Xor, Max, Min, Plus, Times}
  def plus  (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Plus .id)
  def times (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Times.id)
  def min   (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Min  .id)
  def max   (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Max  .id)
  def and   (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = And  .id)
  def or    (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Or   .id)
  def xor   (in: GE, size: GE): ReduceWindow = apply(in, size = size, op = Xor  .id)
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
    val op0 = BinaryOp.Op(op)

    op0 match {
      case opII: BinaryOp.OpII if in.isInt  =>
        stream.ReduceWindow[Int   , BufI](op0.name, opII.funII, in = in.toInt   , size = size.toInt): StreamOut
      case opLL: BinaryOp.OpLL if in.isLong || in.isInt =>
        stream.ReduceWindow[Long  , BufL](op0.name, opLL.funLL, in = in.toLong  , size = size.toInt): StreamOut
      case _ =>
        stream.ReduceWindow[Double, BufD](op0.name, op0 .funDD, in = in.toDouble, size = size.toInt): StreamOut
    }
  }
}
