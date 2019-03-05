/*
 *  Concat.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

/** Concatenates two signals. */
final case class Concat(a: GE, b: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit builder: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(a.expand, b.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit builder: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit builder: stream.Builder): StreamOut = {
    val Vec(a, b) = args
    stream.Concat(a = a.toDouble, b = b.toDouble)
  }
}