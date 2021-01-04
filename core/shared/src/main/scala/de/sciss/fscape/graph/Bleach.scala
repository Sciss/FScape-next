/*
 *  Bleach.scala
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

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

final case class Bleach(in: GE, filterLen: GE = 256, feedback: GE = 0.001, filterClip: GE = 8.0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, filterLen.expand, feedback.expand, filterClip.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, filterLen, feedback, filterClip) = args
    stream.Bleach(in = in.toDouble, filterLen = filterLen.toInt, feedback = feedback.toDouble,
      filterClip = filterClip.toDouble)
  }
}