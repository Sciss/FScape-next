/*
 *  WindowMaxIndex.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutB
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

object WindowMaxIndex {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI)(implicit b: Builder, tpe: StreamType[A, E]): OutI = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "WindowMaxIndex"

  private type Shp[E] = FanInShape2[E, BufI, BufI]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet[E](s"$name.in"  ),
      in1 = InI     (s"$name.size"),
      out = OutI    (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)(_ > _)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)(_ > _)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)(_ > _)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                                  (gt: (A, A) => Boolean)
                                               (implicit ctrl: Control, protected val tpe: StreamType[A, E])
    extends FilterWindowedInAOutB[A, E, Int, BufI, A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize = Handlers.InIAux(this, shape.in1)(math.max(0 , _))

    private[this] var index   : Int = _
    private[this] var maxValue: A   = _

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext
      if (ok) {
        hSize.next()
        index     = -1
        maxValue  = tpe.minValue
      }
      ok
    }

    protected def winBufSize: Int = 0

    override protected def readWinSize  : Long = hSize.value
    override protected def writeWinSize : Long = 1

    protected def processWindow(): Unit = ()

    override protected def readIntoWindow(n: Int): Unit = {
      val in      = hIn.array
      val inOff   = hIn.offset
      var i       = inOff
      val stop    = i + n
      var _index  = index
      var _max    = maxValue
      val d       = readOff.toInt - inOff
      while (i < stop) {
        val v = in(i)
        if (gt(v, _max)) {
          _max    = v
          _index  = i + d
        }
        i += 1
      }
      maxValue  = _max
      index     = _index
      hIn.advance(n)
    }

    override protected def writeFromWindow(n: Int): Unit = {
      assert (n == 1)
      hOut.next(index)
    }
  }
}