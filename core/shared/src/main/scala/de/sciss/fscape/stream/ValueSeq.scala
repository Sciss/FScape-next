/*
 *  ValueSeq.scala
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

import akka.stream.{Attributes, Inlet, Outlet, SourceShape}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

object ValueSeq {
  def int(elems: Array[Int])(implicit b: Builder): OutI = {
    val stage0  = new Stage[Int, BufI](b.layer, elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def long(elems: Array[Long])(implicit b: Builder): OutL = {
    val stage0  = new Stage[Long, BufL](b.layer, elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def double(elems: Array[Double])(implicit b: Builder): OutD = {
    val stage0  = new Stage[Double, BufD](b.layer, elems)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "ValueSeq"

  private type Shp[E] = SourceShape[E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, elems: Array[A])
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new SourceShape(
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer, elems.asInstanceOf[Array[Double ]])
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer, elems.asInstanceOf[Array[Int    ]])
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer, elems.asInstanceOf[Array[Long   ]])
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer, elems: Array[A])
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hOut  = Handlers.OutMain[A, E](this, shape.out)
    private[this] var index = 0

    protected def onDone(inlet: Inlet[_]): Unit = assert(false)

    protected def process(): Unit = {
      var i     = index
      val rem   = math.min(hOut.available, elems.length - i)
      val buf   = hOut.array
      var off   = hOut.offset
      val stop  = off + rem
      while (off < stop) {
        buf(off) = elems(i)
        i   += 1
        off += 1
      }
      hOut.advance(rem)
      index = i
      if (i == elems.length && hOut.flush()) completeStage()
    }
  }
}