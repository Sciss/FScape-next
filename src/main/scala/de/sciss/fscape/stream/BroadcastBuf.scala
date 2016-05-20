/*
 *  BroadcastBuf.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.GraphDSL
import de.sciss.fscape.stream.impl.BroadcastBufStageImpl

import scala.collection.immutable.{IndexedSeq => Vec}

object BroadcastBuf {
  def apply[B <: BufLike](in: Outlet[B], numOutputs: Int)
                         (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Vec[Outlet[B]] = {
    val stage0 = new BroadcastBufStageImpl[B](numOutputs = numOutputs, eagerCancel = true)
    val stage  = b.add(stage0)
    import GraphDSL.Implicits._
    in ~> stage.in
    stage.outArray.toIndexedSeq
  }
}
