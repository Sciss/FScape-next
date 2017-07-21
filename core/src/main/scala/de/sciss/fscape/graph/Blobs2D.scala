/*
 *  Blobs2D.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A blob detection UGen. It is based on the meta-balls algorithm by
  * Julien Gachadoat (http://www.v3ga.net/processing/BlobDetection/).
  *
  * Currently, we output four channels:
  * - 0 - `numBlobs` - flushes for each window one element with the number of blobs detected
  * - 1 - `bounds` - quadruplets of `xMin`, `xMax`, `yMin`, `yMax` - for each blob
  * - 2 - `numVertices` - the number of vertices (contour coordinates) for each blob
  * - 3 - `vertices` - tuples of `x` and `y` for the vertices of each blob
  *
  * @param in         the image(s) to analyse
  * @param width      the width of the image
  * @param height     the height of the image
  * @param thresh     the threshold for blob detection between zero and one
  * @param pad        size of "border" to put around input matrices. If greater than
  *                   zero, a border of that size is created internally, filled with
  *                   the threshold value
  */
final case class Blobs2D(in: GE, width: GE, height: GE, thresh: GE = 0.3, pad: GE = 0)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, width.expand, height.expand, thresh.expand, pad.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, 4)

  def numBlobs    : GE = ChannelProxy(this, 0)
  def bounds      : GE = ChannelProxy(this, 1)
  def numVertices : GE = ChannelProxy(this, 2)
  def vertices    : GE = ChannelProxy(this, 3)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(in, width, height, thresh, pad) = args
    val (out0, out1, out2, out3) = stream.Blobs2D(in = in.toDouble, width = width.toInt, height = height.toInt,
      thresh = thresh.toDouble, pad = pad.toInt)
    Vector[StreamOut](out0, out1, out2, out3)
  }
}