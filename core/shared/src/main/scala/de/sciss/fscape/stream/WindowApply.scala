/*
 *  WindowApply.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InMain, OutMain}
import de.sciss.fscape.stream.impl.logic.WindowedInAOutA
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers.{IntFunctions => ri}

import scala.annotation.switch
import scala.math.max

object WindowApply {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, index: OutI, mode: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(index , stage.in2)
    b.connect(mode  , stage.in3)

    stage.out
  }

  private final val name = "WindowApply"

  private type Shp[E] = FanInShape4[E, BufI, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape4(
      in0 = Inlet [E] (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InI       (s"$name.index"),
      in3 = InI       (s"$name.mode" ),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, protected val tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) with WindowedInAOutA[A, E] {

    protected     val hIn   : InMain  [A, E]  = InMain  (this, shape.in0)
    protected     val hOut  : OutMain [A, E]  = OutMain (this, shape.out)
    private[this] val hSize : InIAux          = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hIdx  : InIAux          = InIAux  (this, shape.in2)()
    private[this] val hMode : InIAux          = InIAux  (this, shape.in3)(ri.clip(_, 0, 3))

    private[this] var elem    : A   = _
    private[this] var winSize : Int = _
    private[this] var index   : Int = _
    private[this] var found   : Boolean = _

    protected def winBufSize: Int = 0

    override protected def readWinSize  : Long = winSize
    override protected def writeWinSize : Long = 1

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hIdx.hasNext && hMode.hasNext
      if (ok) {
        winSize     = hSize .next()
        val index0  = hIdx  .next()
        val mode    = hMode .next()

        index =
          if (index0 >= 0 && index0 < winSize) index0
          else (mode: @switch) match {
            case 0 => ri.clip(index0, 0, winSize - 1)
            case 1 => ri.wrap(index0, 0, winSize - 1)
            case 2 => ri.fold(index0, 0, winSize - 1)
            case 3 =>
              elem = tpe.zero
              -1
          }

        found = false
      }
      ok
    }

    override protected def readIntoWindow(n: Int): Unit = {
      val writeOffI = readOff.toInt
      val stop      = writeOffI + n
      val _index    = index
      if (_index >= writeOffI && _index < stop) {
        assert (!found)
        val in        = hIn.array
        val mainInOff = hIn.offset
        elem  = in(_index - writeOffI + mainInOff)
        found = true
        hIn.advance(n)
      } else {
        hIn.skip(n)
      }
    }

    override protected def writeFromWindow(n: Int): Unit = {
      assert (n == 1)
      val v = if (found) elem else tpe.zero
      hOut.next(v)
    }

    protected def processWindow(): Unit = ()
  }
}