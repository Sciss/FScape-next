/*
 *  DebugSink.scala
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

package de.sciss.fscape.graph

import akka.stream.scaladsl.{GraphDSL, Sink}
import de.sciss.fscape.stream.StreamIn
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A debugging UGen that installs a persistent no-op sink,
  * allowing the `in` UGen to remain in the graph even if
  * it does not have a side-effect and it is not connected
  * to any other graph element.
  *
  * @param in   the element to keep inside the graph
  */
case class DebugSink(in: GE) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in) = args
    val peer = in.toAny
    implicit val dsl = b.dsl
    import GraphDSL.Implicits._
    peer ~> Sink.ignore
  }
}