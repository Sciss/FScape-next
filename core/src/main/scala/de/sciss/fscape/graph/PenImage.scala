/*
 *  PenImage.scala
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

/** A UGen that writes the pixels of an image using an `x` and `y` input signal.
  * It uses either a sinc-based band-limited resampling algorithm, or
  * bicubic interpolation, depending on the `zeroCrossings` parameter.
  *
  * All window defining parameters (`width`, `height`)
  * are polled once per matrix. All writing and filter parameters are polled one per
  * output pixel.
  *
  * @param src            the source signal's amplitude or "pen color"
  * @param alpha          the alpha component of the source signal (0.0 transparent to 1.0 opaque).
  * @param dst            the "background" image to draw on. A `DC(0.0)` can be used, for example,
  *                       to have a "black" background.
  * @param width          the width (number of columns) of the input and output matrix
  * @param height         the height (number of rows) of the input and output matrix
  * @param x              horizontal position of the dynamic pen signal
  * @param y              vertical position of the dynamic pen signal
  * @param next           a trigger that causes the UGen to emit the current image and begin a new one.
  *                       An image of size `width * height` will be output, and new background data will
  *                       be read from `in`.
  * @param rule           quasi-Porter-Duff rule id for composition between background (`in`)
  *                       and pen foreground. It is assumed that
  *                       `<em>A<sub>r</sub></em> = <em>A<sub>d</sub></em> = <em>1</em>`,
  *                       and instead of addition we use a custom binary operation `op`.
  *                       Where the constrain leads to otherwise identical rules, we flip the
  *                       operand order (e.g. `SrcOver` versus `SrcAtop`).
  * @param op             `BinaryOp.Op` identifier for the operand in the application of the
  *                       Porter-Duff composition (`+` in the standard definition).
  * @param wrap           if non-zero, wraps coordinates around the input images boundaries.
  *                       __TODO:__ currently `wrap = 0` is broken if using sinc interpolation!
  * @param rollOff        the FIR anti-aliasing roll-off width. Between zero and one.
  * @param kaiserBeta     the FIR windowing function's parameter
  * @param zeroCrossings  the number of zero-crossings in the truncated and windowed sinc FIR.
  *                       If zero (default), algorithm uses bicubic interpolation instead.
  *
  * @see [[ScanImage]]
  */
final case class PenImage(src: GE = 1.0, alpha: GE = 1.0, dst: GE = 0.0,
                          width: GE, height: GE, x: GE = 0, y: GE = 0, next: GE = 0,
                          rule: GE = PenImage.SrcOver, op: GE = BinaryOp.Plus.id, wrap: GE = 1,
                          rollOff: GE = 0.86, kaiserBeta: GE = 7.5, zeroCrossings: GE = 0)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(
      src   .expand, alpha  .expand, dst.expand,
      width .expand, height .expand, x  .expand, y.expand, next.expand,
      rule  .expand, op     .expand,
      wrap  .expand, rollOff.expand, kaiserBeta.expand, zeroCrossings.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(src, alpha, dst, width, height, x, y, next, rule, op, wrap, rollOff, kaiserBeta, zeroCrossings) = args
    stream.PenImage(src = src.toDouble, alpha = alpha.toDouble, dst = dst.toDouble,
      width = width.toInt, height = height.toInt, x = x.toDouble, y = y.toDouble, next = next.toInt,
      rule = rule.toInt, op = op.toInt,
      wrap = wrap.toInt,
      rollOff = rollOff.toDouble, kaiserBeta = kaiserBeta.toDouble, zeroCrossings = zeroCrossings.toInt)
  }
}
object PenImage {
  /**
    * `<em>C<sub>r</sub></em> = 0`
    *
    * Same as `SrcOut`
    */
  final val Clear   = 1

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>s</sub></em>`
    *
    * Same as `SrcIn`
    */
  final val Src     = 2

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>`
    *
    * Same as `DstOver`
    */
  final val Dst     = 9

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>s</sub></em> _op_ <em>C<sub>d</sub></em>*(1-<em>A<sub>s</sub></em>)`
    *
    * Like `SrcAtop` but swapped operands.
    */
  final val SrcOver = 3

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>`
    *
    * Same as `Dst`
    */
  final val DstOver = 4

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>s</sub></em>`
    *
    * Same as `Src`
    */
  final val SrcIn   = 5

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>*<em>A<sub>s</sub></em>`
    *
    * Same as `DstAtop`
    */
  final val DstIn   = 6

  /**
    * `<em>C<sub>r</sub></em> = 0`
    *
    * Same as `Clear`
    */
  final val SrcOut  = 7

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>*(1-<em>A<sub>s</sub></em>)`
    *
    * Same as `Xor`
    */
  final val DstOut  = 8

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>*(1-<em>A<sub>s</sub></em>) _op_ <em>C<sub>s</sub></em>`
    *
    * Like `SrcOver` but swapped operands.
    */
  final val SrcAtop = 10

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>*<em>A<sub>s</sub></em>`
    *
    * Same as `DstIn`
    */
  final val DstAtop = 11

  /**
    * `<em>C<sub>r</sub></em> = <em>C<sub>d</sub></em>*(1-<em>A<sub>s</sub></em>)`
    *
    * Same as `Xor`
    */
  final val Xor     = 12
}