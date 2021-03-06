/*
 *  ControlBlockSize.scala
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
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object ControlBlockSize extends ProductReader[ControlBlockSize] {
  override def read(in: RefMapIn, key: String, arity: Int): ControlBlockSize = {
    require (arity == 0)
    new ControlBlockSize
  }
}
/** A scalar information UGen that reports the control block size. */
final case class ControlBlockSize() extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = makeUGen(Vector.empty)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder) =
    ConstantI(b.control.blockSize).toInt
}
