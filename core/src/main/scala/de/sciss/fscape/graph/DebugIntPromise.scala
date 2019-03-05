/*
 *  DebugPromise.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Promise

/** A debugging UGen that completes a promise with the input values it sees.
  *
  * @param in   the signal to monitor
  */
case class DebugIntPromise(in: GE, p: Promise[Vec[Int]]) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in) = args
    stream.DebugIntPromise(in.toInt, p)
  }
}