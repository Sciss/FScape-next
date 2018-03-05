/*
 *  FoldCepstrum.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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