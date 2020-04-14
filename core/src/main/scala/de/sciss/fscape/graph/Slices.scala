/*
 *  Slices.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
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

/** A UGen that assembles slices of an input signal in
  * random access fashion. It does so by buffering the
  * input to disk.
  *
  * @param in     the signal to re-arrange.
  * @param spans  successive frame start (inclusive) and
  *               stop (exclusive) frame
  *               positions determining the spans that
  *               are output by the UGen. This parameter
  *               is read on demand. First, the first two
  *               values are read, specifying the first span.
  *               Only after this span has been output,
  *               the next two values from `spans` are
  *               read, and so on. Values are clipped to
  *               zero (inclusive) and the length of the
  *               input signal (exclusive). If a
  *               start position is greater than a stop
  *               position, the span is output in reversed order.
  *
  * @see [[ScanImage]]
  */
final case class Slices(in: GE, spans: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, spans.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, spans) = args
    import in.tpe
    val out = stream.Slices[in.A, in.Buf](in = in.toElem, spans = spans.toLong)
    tpe.mkStreamOut(out)
  }
}