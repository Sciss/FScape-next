/*
 *  PitchesToViterbi.scala
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
package stream

import akka.stream.{Attributes, FanInShape8, Outlet}
import de.sciss.fscape.stream.impl.{DemandWindowedLogic, NodeImpl, Out1DoubleImpl, Out1LogicImpl, StageImpl}

object PitchesToViterbi {
  def apply(lags: OutD, strengths: OutD, n: OutI, voicingThresh: OutD, silenceThresh: OutD, octaveCost: OutD,
            octaveJumpCost: OutD, voicedUnvoicedCost: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(lags              , stage.in0)
    b.connect(strengths         , stage.in1)
    b.connect(n                 , stage.in2)
    b.connect(voicingThresh     , stage.in3)
    b.connect(silenceThresh     , stage.in4)
    b.connect(octaveCost        , stage.in5)
    b.connect(octaveJumpCost    , stage.in6)
    b.connect(voicedUnvoicedCost, stage.in7)
    stage.out
  }

  private final val name = "PitchesToViterbi"

  private type Shape = FanInShape8[BufD, BufD, BufI, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape8(
      in0 = InD (s"$name.lags"              ),
      in1 = InD (s"$name.strengths"         ),
      in2 = InI (s"$name.n"                 ),
      in3 = InD (s"$name.voicingThresh"     ),
      in4 = InD (s"$name.silenceThresh"     ),
      in5 = InD (s"$name.octaveCost"        ),
      in6 = InD (s"$name.octaveJumpCost"    ),
      in7 = InD (s"$name.voicedUnvoicedCost"),
      out = OutD(s"$name.out"               )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape]
      with Out1DoubleImpl[Shape] with Out1LogicImpl[BufD, Shape] {

    protected var bufOut0: BufD = _

    protected def out0: Outlet[BufD] = shape.out

    protected def mainCanRead: Boolean = ???

    protected def auxCanRead: Boolean = ???

    def inValid: Boolean = ???

    ??? // install handlers

    protected def readMainIns(): Int = ???

    protected def readAuxIns(): Int = ???

    protected def inputsEnded: Boolean = ???

    protected def freeOutputBuffers(): Unit = ???

    protected def startNextWindow(): Long = ???

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = ???

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = ???

    protected def processWindow(writeToWinOff: Long): Long = ???
  }
}
