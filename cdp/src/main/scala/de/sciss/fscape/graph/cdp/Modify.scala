/*
 *  Modify.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph
package cdp

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object Modify {
  object Radical {
    final case class Reverse(in: GE) extends UGenSource.SingleOut {
      override def productPrefix = s"Modify$$Radical$$Reverse"

      protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
        unwrap(this, Vector(in.expand))

      protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
        UGen.SingleOut(this, args)

      private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
        val Vec(in) = args
        stream.CdpModifyRadicalReverse(in.toDouble)
      }
    }
  }
}