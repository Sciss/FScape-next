/*
 *  ARCWindow.scala
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

import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl, WindowedLogicD}
import Handlers._

object ARCWindow {
  def apply(in: OutD, size: OutI, lo: OutD, hi: OutD, lag: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(lo  , stage.in2)
    b.connect(hi  , stage.in3)
    b.connect(lag , stage.in4)
    stage.out
  }

  private final val name = "ARCWindow"

  private type Shape = FanInShape5[BufD, BufI, BufD, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape: Shape = new FanInShape5(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      in2 = InD (s"$name.lo"  ),
      in3 = InD (s"$name.hi"  ),
      in4 = InD (s"$name.lag" ),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedLogicD[Shape] {

    protected     val hIn   : InDMain   = InDMain  (this, shape.in0)
    protected     val hOut  : OutDMain  = OutDMain (this, shape.out)
    private[this] val hSize : InIAux    = InIAux   (this, shape.in1)(math.max(0, _))
    private[this] val hLo   : InDAux    = InDAux   (this, shape.in2)()
    private[this] val hHi   : InDAux    = InDAux   (this, shape.in3)()
    private[this] val hLag  : InDAux    = InDAux   (this, shape.in4)()

    private[this] var init    = true
    private[this] var minMem  = 0.0
    private[this] var maxMem  = 0.0

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hLo.hasNext && hHi.hasNext && hLag.hasNext
      if (ok) {
        hSize.next()  // the others are called in `processWindow`
      }
      ok
    }

    protected def winBufSize: Int = hSize.value

    override protected val fullLastWindow: Boolean = false

    protected def processWindow(): Unit = {
      val lo  = hLo .next()
      val hi  = hHi .next()
      val lag = hLag.next()

      val num = readOff.toInt
      if (num == 0) return

      val b   = winBuf
      var min = b(0)
      var max = b(0)

      {
        var i = 1
        while (i < num) {
          val x = b(i)
          if      (x > max) max = x
          else if (x < min) min = x
          i += 1
        }
      }

      if (init) {
        init = false
        minMem = min
        maxMem = max
      } else {
        val cy = lag
        val cx = 1.0 - math.abs(cy)
        minMem = minMem * cy + min * cx
        maxMem = maxMem * cy + max * cx
      }

      if (minMem == maxMem) {
        Util.fill(b, 0, num, lo)
      } else {
        val mul = (hi - lo) / (maxMem - minMem)
        val add = lo - minMem * mul
        var i = 0
        while (i < num) {
          val x0 = b(i)
          val y1 = x0 * mul + add
          b(i) = y1
          i += 1
        }
      }
    }
  }
}