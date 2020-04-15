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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.Handlers.{InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.logic.WindowedInDOutD
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

object ReduceWindow {
  import graph.BinaryOp.Op

  def apply(in: OutD, size: OutI, op: Op)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer, op)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "ReduceWindow"

  private type Shp = FanInShape2[BufD, BufI, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      out = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, op)
  }

  private final class Logic(shape: Shp, layer: Layer, op: Op)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) with WindowedInDOutD {

    type A = Double

    override protected  val hIn   : InDMain  = InDMain  (this, shape.in0)
    override protected  val hOut  : OutDMain = OutDMain (this, shape.out)
    private[this]       val hSize : InIAux   = InIAux   (this, shape.in1)(math.max(0 , _))

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
        _value  = op.funDD(_value, v)
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