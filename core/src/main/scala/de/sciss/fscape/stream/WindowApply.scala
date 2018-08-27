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
import de.sciss.fscape.stream.impl.{FilterIn4Impl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}
import de.sciss.numbers.IntFunctions

object WindowApply {
  def apply[A, BufA >: Null <: BufElem[A]](in: Outlet[BufA], size: OutI, index: OutI, wrap: OutI)
                                          (implicit b: Builder, aTpe: StreamType[A, BufA]): Outlet[BufA] = {
    val stage0  = new Stage[A, BufA]
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(index , stage.in2)
    b.connect(wrap  , stage.in3)

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
      in3 = InI         (s"$name.wrap" ),
      out = Outlet[BufA](s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic[A, BufA >: Null <: BufElem[A]](shape: Shape[A, BufA])
                                                          (implicit ctrl: Control, aTpe: StreamType[A, BufA])
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape[A, BufA]]
      with FilterLogicImpl[BufA, Shape[A, BufA]]
      with FilterIn4Impl[BufA, BufI, BufI, BufI, BufA] {

    private[this] var elem        : A       = _
    private[this] var winSize     : Int     = _
    private[this] var index0      : Int     = _
    private[this] var index       : Int     = _
    private[this] var wrap        : Boolean = _

    protected def allocOutBuf0(): BufA = aTpe.allocBuf()

    protected def startNextWindow(inOff: Int): Long = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        winSize = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        index0 = bufIn2.buf(inOff)
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        wrap = bufIn3.buf(inOff) != 0
      }

      index =
        if (index0 >= 0 && index0 < winSize) index0
        else if (wrap)  IntFunctions.wrap(index0, 0, winSize - 1)
        else            IntFunctions.clip(index0, 0, winSize - 1)

      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit = {
      val writeOffI = writeToWinOff.toInt
      val stop      = writeOffI + chunk
      val _index    = index
      if (_index >= writeOffI && _index < stop) {
        elem = bufIn0.buf(_index - writeOffI + inOff)
      }
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      assert(readFromWinOff == 0 && chunk == 1)
      bufOut0.buf(outOff) = elem
    }

    protected def processWindow(writeToWinOff: Long): Long = 1
  }
}