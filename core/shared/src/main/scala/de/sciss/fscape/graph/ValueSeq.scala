/*
 *  ValueSeq.scala
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

import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** Loops the given values. */
object ValueSeq {
  def apply(elem0: Int    , rest: Int   *): GE = ValueIntSeq    (elem0 +: rest: _*)
  def apply(elem0: Long   , rest: Long  *): GE = ValueLongSeq   (elem0 +: rest: _*)
  def apply(elem0: Double , rest: Double*): GE = ValueDoubleSeq (elem0 +: rest: _*)
}

/** Loops the given values. */
final case class ValueIntSeq(elems: Int*) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    makeUGen(elems.iterator.map(x => x: UGenIn).toIndexedSeq)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val elems: Array[Int] = args.iterator.collect {
      case c: ConstantI => c.value
    } .toArray

    stream.ValueSeq.int(elems)
  }
}

/** Loops the given values. */
final case class ValueLongSeq(elems: Long*) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    makeUGen(elems.iterator.map(x => x: UGenIn).toIndexedSeq)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val elems: Array[Long] = args.iterator.collect {
      case c: ConstantL => c.value
    } .toArray

    stream.ValueSeq.long(elems)
  }
}

/** Loops the given values. */
final case class ValueDoubleSeq(elems: Double*) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    makeUGen(elems.iterator.map(x => x: UGenIn).toIndexedSeq)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val elems: Array[Double] = args.iterator.collect {
      case c: ConstantD => c.value
    } .toArray

    stream.ValueSeq.double(elems)
  }
}