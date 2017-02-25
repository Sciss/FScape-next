/*
 *  Dseq.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** XXX TODO --- not implemented yet.
  * A UGen that cycles over a list of values.
  *
  * @param seq      sequence of values to be returned
  * @param repeats  the number of repetitions of the sequence
  */
final case class Dseq(seq: GE, repeats: GE = 1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, repeats.expand +: seq.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val repeats +: seq = args
    ??? // stream.Dseq(seq = seq.map(_.toAny), repeats = repeats.toLong)
  }
}

///** A UGen that cycles over a list of values.
//  *
//  * As opposed to ScalaCollider, this is
//  * called `Dcycle` instead of `Dseq`, because the latter name can
//  * be confused with `ArithmSeq`.
//  *
//  * @param seq      sequence of values to be returned
//  */
//final case class Dcycle(seq: GE) extends UGenSource.SingleOut {
//  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
//    unwrap(this, seq.expand.outputs)
//
//  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
//    UGen.SingleOut(this, args)
//
//  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
//    val seq = args
//    ??? // stream.Dcycle(seq = seq.map(_.toAny))
//  }
//}