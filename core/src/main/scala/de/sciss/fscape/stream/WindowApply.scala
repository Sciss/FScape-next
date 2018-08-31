/*
 *  WindowApply.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{DemandFilterIn4, DemandWindowedLogic, NodeImpl, StageImpl}
import de.sciss.numbers.IntFunctions

import scala.annotation.switch

object WindowApply {
  def apply[A, BufA >: Null <: BufElem[A]](in: Outlet[BufA], size: OutI, index: OutI, mode: OutI)
                                          (implicit b: Builder, aTpe: StreamType[A, BufA]): Outlet[BufA] = {
    val stage0  = new Stage[A, BufA]
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(index , stage.in2)
    b.connect(mode  , stage.in3)

    stage.out
  }

  private final val name = "WindowApply"

  private type Shape[A, BufA >: Null <: BufElem[A]] =
    FanInShape4[BufA, BufI, BufI, BufI, BufA]

  private final class Stage[A, BufA >: Null <: BufElem[A]](implicit ctrl: Control, aTpe: StreamType[A, BufA])
    extends StageImpl[Shape[A, BufA]](name) {

    val shape = new FanInShape4(
      in0 = Inlet[BufA] (s"$name.in"   ),
      in1 = InI         (s"$name.size" ),
      in2 = InI         (s"$name.index"),
      in3 = InI         (s"$name.mode" ),
      out = Outlet[BufA](s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic[A, BufA >: Null <: BufElem[A]](shape: Shape[A, BufA])
                                                          (implicit ctrl: Control, aTpe: StreamType[A, BufA])
    extends NodeImpl(name, shape)
      with DemandWindowedLogic[Shape[A, BufA]]
      with DemandFilterIn4[BufA, BufI, BufI, BufI, BufA] {

    private[this] var elem        : A       = _
    private[this] var winSize     : Int     = _
    private[this] var index0      : Int     = _
    private[this] var index       : Int     = _
    private[this] var mode        : Int     = _
    private[this] val zero        : A       = aTpe.newArray(1)(0)

    protected def allocOutBuf0(): BufA = aTpe.allocBuf()

    protected def inputsEnded: Boolean =
      mainInRemain == 0 && isClosed(in0) && !isAvailable(in0)

    protected def startNextWindow(): Long = {
      val inOff = auxInOff
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        index0 = bufIn2.buf(inOff)
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        mode = math.max(0, math.min(3, bufIn3.buf(inOff)))
      }

      index =
        if (index0 >= 0 && index0 < winSize) index0
        else (mode: @switch) match {
          case 0 => IntFunctions.clip(index0, 0, winSize - 1)
          case 1 => IntFunctions.wrap(index0, 0, winSize - 1)
          case 2 => IntFunctions.fold(index0, 0, winSize - 1)
          case 3 =>
            elem = zero
            -1
        }

      winSize
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && {
      isClosed(in1) && isClosed(in2) && isClosed(in3)
    })

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      val writeOffI = writeToWinOff.toInt
      val stop      = writeOffI + chunk
      val _index    = index
      if (_index >= writeOffI && _index < stop) {
        elem = bufIn0.buf(_index - writeOffI + mainInOff)
      }
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      assert(readFromWinOff == 0 && chunk == 1)
      bufOut0.buf(outOff) = elem
    }

    protected def processWindow(writeToWinOff: Long): Long = 1
  }
}