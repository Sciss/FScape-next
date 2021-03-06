/*
 *  Reduce.scala
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

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object Reduce extends ProductReader[Reduce] {
  import BinaryOp.{And, Or, Xor, Max, Min, Plus, Times}
  /** Same result as `Mix( _ )` */
  def +  (elem: GE): Reduce = apply(elem, Plus  .id)
  def *  (elem: GE): Reduce = apply(elem, Times .id)
  //   def all_sig_==( elem: GE ) = ...
  //   def all_sig_!=( elem: GE ) = ...
  def min(elem: GE): Reduce = apply(elem, Min   .id)
  def max(elem: GE): Reduce = apply(elem, Max   .id)
  def &  (elem: GE): Reduce = apply(elem, And.id)
  def |  (elem: GE): Reduce = apply(elem, Or .id)
  def ^  (elem: GE): Reduce = apply(elem, Xor.id)

  override def read(in: RefMapIn, key: String, arity: Int): Reduce = {
    require (arity == 2)
    val _elem = in.readGE()
    val _op   = in.readInt()
    new Reduce(_elem, _op)
  }
}

final case class Reduce(elem: GE, op: Int) extends UGenSource.SingleOut {
  // XXX TODO: should not be UGenSource

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    UGenSource.unwrap(this, elem.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike = args match {
    case head +: tail =>
      val op1 = BinaryOp.Op(op)
      tail.foldLeft(head: UGenInLike) { (a, b) =>
        op1.make(a, b).expand
      }
    case _ => UGenInGroup.empty
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut =
    throw new UnsupportedOperationException // XXX TODO --- not pretty
}
