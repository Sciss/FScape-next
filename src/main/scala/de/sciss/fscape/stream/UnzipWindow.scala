/*
 *  UnzipWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import akka.stream.scaladsl.GraphDSL
import de.sciss.fscape.stream.impl.UnzipWindowStageImpl

import scala.collection.immutable.{IndexedSeq => Vec}

/** Unzips a signal into two based on a window length. */
object UnzipWindow {
  /**
    * @param in     the signal to unzip
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: OutD, size: OutI)(implicit b: GBuilder, ctrl: Control): (OutD, OutD) = {
    val Seq(out0, out1) = UnzipWindowN(2, in = in, size = size)
    (out0, out1)
  }
}

/** Unzips a signal into a given number of outputs based on a window length. */
object UnzipWindowN {
  /**
    * @param numOutputs the number of outputs to deinterleave the input into
    * @param in         the signal to unzip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply(numOutputs: Int, in: OutD, size: OutI)(implicit b: GBuilder, ctrl: Control): Vec[OutD] = {
    val stage0  = new UnzipWindowStageImpl(numOutputs = numOutputs)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1

    stage.outlets.toIndexedSeq
  }
}