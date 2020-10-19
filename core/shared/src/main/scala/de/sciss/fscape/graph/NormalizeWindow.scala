/*
 *  NormalizeWindow.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object NormalizeWindow {
  final val Normalize   = 0
  final val FitUnipolar = 1
  final val FitBipolar  = 2
  final val ZeroMean    = 3

  final val ModeMax     = 3

  def normalize   (in: GE, size: GE): GE = NormalizeWindow(in, size, mode = Normalize   )
  def fitUnipolar (in: GE, size: GE): GE = NormalizeWindow(in, size, mode = FitUnipolar )
  def fitBipolar  (in: GE, size: GE): GE = NormalizeWindow(in, size, mode = FitBipolar  )
  def zeroMean    (in: GE, size: GE): GE = NormalizeWindow(in, size, mode = ZeroMean    )
}

/** A UGen that normalizes each input window according to a mode.
  * It can be used for normalizing the value range or removing DC offset.
  * If the last window is not entirely filled, the output will pad that
  * window always to zero (no matter the normalization mode!)
  *
  * A window size of 1 should be avoided (and does not really make sense),
  * although the UGen makes efforts to not output NaN values.
  *
  * @param in     the input signal
  * @param size   the input's window size
  * @param mode   `0` for normalizing the amplitude to 1,
  *               `1` for fitting into the range of 0 to 1,
  *               `2` for fitting into the range of -1 to 1,
  *               `3` for removing DC (creating a mean of zero).
  */
final case class NormalizeWindow(in: GE, size: GE, mode: GE = NormalizeWindow.Normalize)
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand, mode.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, mode) = args
    stream.NormalizeWindow(in = in.toDouble, size = size.toInt, mode = mode.toInt)
  }
}
