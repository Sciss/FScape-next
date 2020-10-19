/*
 *  ReverseWindow.scala
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
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

/** Reverses contents of windowed input. */
object ReverseWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param clump  clump size within each window. With a clump size of one,
    *               each window is reversed sample by sample, if the clump size
    *               is two, the first two samples are flipped with the last
    *               two samples, then the third and forth are flipped with the
    *               third and forth before last, etc. Like `size`, `clump` is
    *               sampled at each beginning of a new window and held constant
    *               during the window.
    */
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, clump: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(clump , stage.in2)
    stage.out
  }

  private final val name = "ReverseWindow"

  private type Shp[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InI       (s"$name.clump"),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isInt)
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      else if (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      else if (tpe.isDouble)
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      else
        new Logic[A, E](shape, layer)

      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                                  (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize : InIAux = InIAux(this, shape.in1)(math.max(0, _))
    private[this] val hClump: InIAux = InIAux(this, shape.in2)(math.max(1, _))

    protected def winBufSize: Int = hSize.value

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hClump.hasNext
      if (ok) {
        hSize .next()
        hClump.next()
      }
      ok
    }

    protected def processWindow(): Unit = {
      val win = winBuf
      val wsz = winBufSize
      var i   = 0
      val cl  = hClump.value
      val cl2 = cl + cl
      var j   = wsz - cl
      while (i < j) {
        val k = i + cl
        while (i < k) {
          val tmp = win(i)
          win(i) = win(j)
          win(j) = tmp
          i += 1
          j += 1
        }
        j -= cl2
      }
    }
  }
}