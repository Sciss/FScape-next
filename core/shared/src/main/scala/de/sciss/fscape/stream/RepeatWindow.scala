/*
 *  RepeatWindowNew.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
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

/** Repeats contents of windowed input.
  */
object RepeatWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param num    the number of times each window is repeated
    */
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, num: OutL)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(num , stage.in2)
    stage.out
  }

  private final val name = "RepeatWindow"

  private type Shp[E] = FanInShape3[E, BufI, BufL, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InL       (s"$name.num"  ),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  // XXX TODO --- optimize for the common case of size = constant 1
  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize = Handlers.InIAux(this, shape.in1)(math.max(0 , _))
    private[this] val hNum  = Handlers.InLAux(this, shape.in2)(math.max(0L, _))

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hNum.hasNext
      if (ok) {
        hSize .next()
        hNum  .next()
      }
      ok
    }

    protected def processWindow(): Unit = ()

    protected def winBufSize: Int = hSize.value

    override protected def writeWinSize: Long = hNum.value * hSize.value

    override protected def writeFromWindow(chunk: Int): Unit = {
      var rem         = chunk
      val in          = winBuf
      var writeOff0   = writeOff
      val winInSize   = winBufSize
      while (rem > 0) {
        val i     = (writeOff0 % winInSize).toInt
        val j     = math.min(rem, winInSize - i)
        hOut.nextN(in, i, j)
//        System.arraycopy(in, i, out, outOff0, j)
        writeOff0 += j
        rem       -= j
      }
    }
  }
}