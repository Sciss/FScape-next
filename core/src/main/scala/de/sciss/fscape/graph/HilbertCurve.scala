/*
 *  HilbertCurve.scala
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
import de.sciss.fscape.stream.{Builder, StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object HilbertCurve {
  /** Encodes two dimensional coordinates as one dimensional indices or positions
    * of a Hilbert curve.
    *
    * The output positions are integers between zero (inclusive)
    * and n-times-n (exclusive). The UGen ends when either `x` or `y` ends.
    *
    * @param  n the square matrix size, which must be a power of two
    * @param  x the integer horizontal coordinate in the n-by-n matrix
    * @param  y the integer vertical coordinate in the n-by-n matrix
    */
  final case class From2D(n: GE, x: GE, y: GE) extends UGenSource.SingleOut {
    override def productPrefix: String = s"HilbertCurve$$From2D"  // serialization

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      unwrap(this, Vector(n.expand, x.expand, y.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
      UGen.SingleOut(this, args)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: Builder): StreamOut = {
      val Vec(n, x, y) = args
      stream.HilbertCurve.From2D(n = n.toInt, x = x.toInt, y = y.toInt)
    }
  }

  /** Decodes one dimensional indices or positions
    * of a Hilbert curve to two dimensional coordinates.
    *
    * The output coordinates are integers between zero (inclusive)
    * and n (exclusive). The UGen ends when `pos` ends.
    *
    * @param  n the square matrix size, which must be a power of two
    * @param  pos the integer position on the hilbert curve from zero (inclusive) to n-times-n (exclusive).
    */
  final case class To2D(n: GE, pos: GE) extends UGenSource.MultiOut {
    override def productPrefix: String = s"HilbertCurve$$To2D"  // serialization

    def x: GE = ChannelProxy(this, 0)
    def y: GE = ChannelProxy(this, 1)

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      unwrap(this, Vector(n.expand, pos.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
      UGen.MultiOut(this, args, numOutputs = 2)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: Builder): Vec[StreamOut] = {
      val Vec(n, pos) = args
      val (x, y) = stream.HilbertCurve.To2D(n = n.toInt, pos = pos.toInt)
      Vector(x, y)
    }
  }
}
