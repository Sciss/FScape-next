/*
 *  GimpSlur.scala
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

/** A UGen similar to GIMP's Slur image filter. Instead of a hard-coded kernel,
  * the probability table must be provided as a separate input.
  * The kernel width and height should be odd, so that the kernel is considered to be
  * symmetric around each input image's pixel. If they are odd, the centre corresponds to
  * integer divisions `kernelWidth/2` and `kernelHeight/2`.
  *
  * @param in             image input
  * @param width          image width
  * @param height         image height
  * @param kernel         normalized and integrated probability table.
  *                       Like the image, the cells are read horizontally first,
  *                       every `widthKernel` cell begins a new cell. The cell probabilities
  *                       must have been integrated from first to last, and must be normalized
  *                       so that the last cell value equals one. A new kernel signal is read
  *                       once per input image. (If the signal ends, the previous kernel will
  *                       be used again).
  * @param kernelWidth    width of the kernel signal. Read once per input image.
  * @param kernelHeight   height of the kernel signal. Read once per input image.
  * @param repeat         number of recursive application of the displacement per image. Read once per input image.
  * @param wrap           if great than zero, wraps pixels around the image bounds, otherwise clips.
  */
final case class GimpSlur(in: GE, width: GE, height: GE, kernel: GE, kernelWidth: GE, kernelHeight: GE,
                          repeat: GE = 1, wrap: GE = 0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, width.expand, height.expand, kernel.expand,
      kernelWidth.expand, kernelHeight.expand, repeat.expand, wrap.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, width, height, kernel, kernelWidth, kernelHeight, repeat, wrap) = args
    stream.GimpSlur(in = in.toDouble, width = width.toInt, height = height.toInt, kernel = kernel.toDouble,
      kernelWidth = kernelWidth.toInt, kernelHeight = kernelHeight.toInt,
      repeat = repeat.toInt, wrap = wrap.toInt)
  }
}