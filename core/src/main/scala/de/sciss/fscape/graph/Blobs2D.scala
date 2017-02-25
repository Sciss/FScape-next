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
  * Currently, we output five channels:
  * - 0 - `numBlobs` - flushes for each window one element with the number of blobs detected
  * - 1 - `xMin` - the minimum horizontal coordinates for each blob
  * - 2 - `xMax` - the maximum horizontal coordinates for each blob
  * - 3 - `yMin` - the minimum vertical coordinates for each blob
  * - 4 - `yMax` - the maximum vertical coordinates for each blob
  *
  * A future version may output the contour coordinates as additional outputs,
  * once we found a good solution for representing polygons.
  *
  * @param in         the image(s) to analyse
  * @param width      the width of the image
  * @param height     the height of the image
  * @param thresh     the threshold for blob detection between zero and one
  */
final case class Blobs2D(in: GE, width: GE, height: GE, thresh: GE = 0.3 /*, minWidth: GE = 0, minHeight: GE = 0 */)
  extends UGenSource.MultiOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, width.expand, height.expand, thresh.expand /* , minWidth.expand, minHeight.expand */))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.MultiOut(this, args, 5)

  def numBlobs: GE = ChannelProxy(this, 0)
  def xMin    : GE = ChannelProxy(this, 1)
  def xMax    : GE = ChannelProxy(this, 2)
  def yMin    : GE = ChannelProxy(this, 3)
  def yMax    : GE = ChannelProxy(this, 4)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
    val Vec(in, width, height, thresh /* , minWidth, minHeight */) = args
    ??? // stream.Blobs2D(in = in.toDouble, width = width.toInt, height = height.toInt, thresh = thresh.toDouble)
  }
}
