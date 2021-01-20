/*
 *  DebugSink.scala
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
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{SinkIgnore, StreamIn}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object DebugSink extends ProductReader[DebugSink] {
  override def read(in: RefMapIn, key: String, arity: Int): DebugSink = {
    require (arity == 1)
    val _in = in.readGE()
    new DebugSink(_in)
  }
}
/** A debugging UGen that installs a persistent no-op sink,
  * allowing the `in` UGen to remain in the graph even if
  * it does not have a side-effect and it is not connected
  * to any other graph element.
  *
  * @param in   the element to keep inside the graph
  */
case class DebugSink(in: GE) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
    UGen.ZeroOut(this, inputs = args)
    ()
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in) = args
    val peer = in.toAny
    SinkIgnore(peer)
  }
}