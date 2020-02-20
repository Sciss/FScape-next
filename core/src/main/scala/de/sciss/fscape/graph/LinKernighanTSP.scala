/*
 *  LinKernighanTSP.scala
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

/** A UGen that solves the traveling salesman problem (TSP) using the Lin-Kernighan heuristic.
  * For each input value of `size`, a corresponding initial tour and edge weight sequence are
  * read, the tour is optimized and output along with the tour's cost.
  *
  * Currently, we output two channels:
  * - 0 - `tour` - the optimized tour
  * - 1 - `cost` - the cost of the optimized tour, i.e. the sum of its edge weights
  *
  * @param init       the initial tour, for example linear or randomized. Should consist of
  *                   `size` zero-based vertex indices
  * @param weights    the symmetric edge weights, a sequence of length `size * (size - 1) / 2`,
  *                   sorted as vertex connections (0,1), (0,2), (0,3), ... (0,size-1),
  *                   (1,2), (1,3), ... (1,size-1), etc., until (size-2,size-1).
  * @param size       for each complete graph, the number of vertices.
  * @param mode       currently unused and should remain the default value of zero.
  */
final case class LinKernighanTSP(init: GE, weights: GE, size: GE, mode: GE = 0)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(init.expand, weights.expand, size.expand, mode.expand))

  def tour: GE = ChannelProxy(this, 0)
  def cost: GE = ChannelProxy(this, 1)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, numOutputs = 2)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(init, weights, size, mode) = args
    val (out0, out1) = stream.LinKernighanTSP(init = init.toInt, weights = weights.toDouble,
      size = size.toInt,
      mode = mode.toInt
    )
    Vector[StreamOut](out0, out1)
  }
}
