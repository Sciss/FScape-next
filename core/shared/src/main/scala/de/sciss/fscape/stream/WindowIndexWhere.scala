/*
 *  WindowIndexWhere.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InIMain, OutIMain}
import de.sciss.fscape.stream.impl.logic.WindowedInAOutA
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.math.max

object WindowIndexWhere {
  def apply(p: OutI, size: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(p   , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "WindowIndexWhere"

  private type Shp = FanInShape2[BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InI (s"$name.p"   ),
      in1 = InI (s"$name.size"),
      out = OutI(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedInAOutA[Int, BufI] {

    override protected  val hIn   : InIMain   = InIMain (this, shape.in0)
    private[this]       val hSize : InIAux    = InIAux  (this, shape.in1)(max(0, _))
    override protected  val hOut  : OutIMain  = OutIMain(this, shape.out)

    private[this] var index: Int = _

    protected def tpe: StreamType[Layer, BufI] = StreamType.int

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext
      if (ok) {
        hSize.next()
        index = -1
      }
      ok
    }

    protected def processWindow(): Unit = ()

    protected def winBufSize: Int = 0

    override protected def readWinSize  : Long = hSize.value
    override protected def writeWinSize : Long = 1 // if (winSize == 0) 0 else 1

    override protected def readIntoWindow(n: Int): Unit =
      if (index < 0) {
        val in    = hIn.array
        val inOff = hIn.offset
        var i     = inOff
        val stop  = i + n
        while (i < stop) {
          if (in(i) != 0) {
            index = readOff.toInt + (i - inOff)
            i = stop  // important to reach `advance`!
          } else {
            i += 1
          }
        }
        hIn.advance(n)
      } else {
        hIn.skip(n)
      }

    override protected def writeFromWindow(n: Int): Unit = {
      assert (n == 1)
      hOut.next(index)
    }
  }
}