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

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.GraphDSL
import de.sciss.fscape.stream.impl.UnzipWindowStageImpl

/** Unzips a signal into two based on a window length. */
object UnzipWindow {
  /**
    * @param in     the signal to unzip
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: Outlet[BufD], size: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): (Outlet[BufD], Outlet[BufD]) = {
    val stage0  = new UnzipWindowStageImpl(numOutputs = 2, ctrl = ctrl)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1

    val Seq(out0, out1) = stage.outlets
    (out0, out1)
  }
}