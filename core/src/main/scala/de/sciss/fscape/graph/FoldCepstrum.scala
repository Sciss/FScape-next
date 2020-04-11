/*
 *  FoldCepstrum.scala
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

object FoldCepstrum {
  def minPhase(in: GE, size: GE): FoldCepstrum =
    FoldCepstrum(
      in = in, size = size,
      crr = 1.0, ccr = 1.0, cri = 1.0, cci = -1.0,
      clr = 0.0, car = 0.0, cli = 0.0, cai =  0.0
    )
}
/**
  * We operate on a complex cepstrum (`size` is the number of complex frames). We distinguish
  * a left `L` (causal) and right `R` (anti-causal) half. The formulas then are
  *
  * {{{
  * reL_out = crr * reL + ccr * reR
  * reR_out = clr * reR + car * reL
  * imL_out = cri * imL + cci * imR
  * imR_out = cli * imR + cai * imL
  * }}}
  *
  * Note that causal and anti-causal are misnamed in the coefficient.
  *
  * For example, to make the signal causal for minimum phase reconstruction: We add the conjugate anti-causal
  * (right) part to the causal (left) part:
  * `crr = 1, ccr = 1, cri = 1, cci = -1`
  * and clear the anti-causal (right) part:
  * `clr = 0, car = 0, cli = 0, cai = 0`
  * (you can just call `FoldCepstrum.minPhase` for this case)
  */
final case class FoldCepstrum(in: GE, size: GE,
            crr: GE, cri: GE, clr: GE, cli: GE,
            ccr: GE, cci: GE, car: GE, cai: GE) extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand, size.expand,
      crr.expand, cri.expand, clr.expand, cli.expand, ccr.expand, cci.expand, car.expand, cai.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in, size, crr, cri, clr, cli, ccr, cci, car, cai) = args
    stream.FoldCepstrum(in = in.toDouble, size = size.toInt,
      crr = crr.toDouble, cri = cri.toDouble, clr = clr.toDouble, cli = cli.toDouble,
      ccr = ccr.toDouble, cci = cci.toDouble, car = car.toDouble, cai = cai.toDouble)
  }
}