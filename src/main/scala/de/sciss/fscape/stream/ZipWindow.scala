/*
 *  ZipWindow.scala
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
import de.sciss.fscape.stream.impl.ZipWindowStageImpl

import scala.collection.immutable.{Seq => ISeq}

/** Zips two signals into one based on a window length. */
object ZipWindow {
  /**
    * @param a      the first signal to zip
    * @param b      the second signal to zip
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply(a: Outlet[BufD], b: Outlet[BufD], size: Outlet[BufI])
           (implicit builder: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] =
    ZipWindowN(in = Vector(a, b), size = size)
}

/** Zips a number of signals into one output based on a window length. */
object ZipWindowN {
  /**
    * @param in         the signals to zip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: ISeq[Outlet[BufD]], size: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new ZipWindowStageImpl(numInputs = in.size)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    (in zip stage.inputs).foreach { case (output, input) =>
      output ~> input
    }
    size ~> stage.size
    stage.out
  }
}