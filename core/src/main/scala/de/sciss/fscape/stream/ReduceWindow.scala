/*
 *  ReduceWindow.scala
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
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object ReduceWindow {
  def apply[A, E <: BufElem[A]](opName: String, op: (A, A) => A, in: Outlet[E], size: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer, opName, op)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "ReduceWindow"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, opName: String, op: (A, A) => A)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E] (s"$name.in"  ),
      in1 = InI       (s"$name.size"),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer, opName, op.asInstanceOf[(Double , Double) => Double ])
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer, opName, op.asInstanceOf[(Int    , Int   ) => Int    ])
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer, opName, op.asInstanceOf[(Long   , Long  ) => Long   ])
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer, opName: String,
                                                                   op: (A, A) => A)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    override def toString = s"$name($opName)@${hashCode.toHexString}"

    private[this] val hSize = InIAux(this, shape.in1)(math.max(0 , _))

    private[this] var value: A = _

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext
      if (ok) {
        hSize.next()
      }
      ok
    }

    protected def winBufSize: Int = 0

    override protected def readWinSize  : Long = hSize.value
    override protected def writeWinSize : Long = 1

    protected def processWindow(): Unit = ()

    override protected def readIntoWindow(chunk: Int): Unit = {
      val in      = hIn.array
      val inOff   = hIn.offset
      var i       = inOff
      val stop    = i + chunk
      var _value  = value
      if (readOff == 0 && chunk > 0) {
        _value = in(inOff)
        i += 1
      }
      while (i < stop) {
        val v   = in(i)
        _value  = op(_value, v)
        i += 1
      }
      value  = _value
      hIn.advance(chunk)
    }

    override protected def writeFromWindow(chunk: Int): Unit = {
      assert(writeOff == 0 && chunk == 1)
      hOut.next(value)
    }
  }
}