/*
 *  RotateWindow.scala
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
package stream

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers.IntFunctions

object RotateWindow {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, amount: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(amount, stage.in2)

    stage.out
  }

  private final val name = "RotateWindow"

  private type Shp[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {
    
    val shape: Shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"    ),
      in1 = InI       (s"$name.size"  ),
      in2 = InI       (s"$name.amount"),
      out = Outlet[E] (s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize   = Handlers.InIAux(this, shape.in1)(math.max(1, _))
    private[this] val hAmount = Handlers.InIAux(this, shape.in2)()

    private[this] var amountInv: Int  = -1

    protected def winBufSize: Int = hSize.value

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hAmount.hasNext
      if (ok) {
        hSize   .next()
        hAmount .next()
      }
      ok
    }

    protected def processWindow(): Unit = {
      val winInSize = winBufSize
      val amount    = hAmount.value
      val amountM   = IntFunctions.mod(amount, winInSize)
      amountInv     = winInSize - amountM
    }

    override protected def writeFromWindow(chunk: Int): Unit = {
      val win       = winBuf
      val winInSize = winBufSize
      val n         = (writeOff.toInt + amountInv) % winInSize
      val m         = math.min(chunk, winInSize - n)
      hOut.nextN(win, n, m)
      val p = chunk - m
      if (p > 0) {
        hOut.nextN(win, 0, p)
      }
    }
  }
}