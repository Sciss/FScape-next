/*
 *  Viterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

/** A UGen that takes concurrent pitch tracker paths, and conditions them for the
  * Viterbi algorithm. The inputs are typically taken from `AutoCorrelationPitches`,
  * and from this a suitable `add` signal is produced to be used in the `Viterbi` UGen.
  *
  * @param lags               pitches given as sample periods, such as returned by
  *                           `AutoCorrelationPitches`.
  * @param strengths          strengths corresponding to the `lags`, such as returned by
  *                           `AutoCorrelationPitches`.
  * @param voicingThresh      threshold for determining whether window is voiced or unvoiced.
  * @param silenceThresh      threshold for determining whether window is background or foreground.
  * @param octaveCost         weighting factor for low versus high frequency preference.
  * @param octaveJumpCost     costs for moving pitches up and down.
  *                           to match the parameters in Praat, you should multiply
  *                           the "literature" value by `0.01 * sampleRate / stepSize`
  *                           (typically in the order of 0.25)
  * @param voicedUnvoicedCost cost for transitioning between voiced and unvoiced segments.
  *                           to match the parameters in Praat,
  *                           the "literature" value by `0.01 * sampleRate / stepSize`
  *                           (typically in the order of 0.25)
  * @param n                  number of paths / candidates
  *
  * see [[AutoCorrelationPitches]]
  * see [[Viterbi]]
  */
final case class PitchesToViterbi(lags: GE, strengths: GE,
                                  n                 : GE = 15,
                                  minLag            : GE,
                                  voicingThresh     : GE = 0.45,
                                  silenceThresh     : GE = 0.03,
                                  octaveCost        : GE = 0.01,
                                  octaveJumpCost    : GE = 0.35,
                                  voicedUnvoicedCost: GE = 0.03
                                 )
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(lags.expand, strengths.expand, n.expand, minLag.expand, voicingThresh.expand,
      silenceThresh.expand, octaveCost.expand, octaveJumpCost.expand, voicedUnvoicedCost.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(lags, strengths, n, minLag, voicingThresh, silenceThresh,
      octaveCost, octaveJumpCost, voicedUnvoicedCost) = args
    stream.PitchesToViterbi(lags = lags.toDouble, strengths = strengths.toDouble, n = n.toInt, minLag = minLag.toInt,
      voicingThresh = voicingThresh.toDouble, silenceThresh = silenceThresh.toDouble,
      octaveCost = octaveCost.toDouble, octaveJumpCost = octaveJumpCost.toDouble,
      voicedUnvoicedCost = voicedUnvoicedCost.toDouble)
  }
}
