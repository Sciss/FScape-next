/*
 *  ComplexUnaryOp.scala
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

import akka.stream.{Attributes, FlowShape, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec

/** Unary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream for most operators (ex. `log`, `carToPol`) or a real
  * stream for some operators (e.g. `real`, `mag`).
  */
object ComplexUnaryOp {
  import graph.ComplexUnaryOp.Op

  def apply(op: Op, in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer, op)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "ComplexUnaryOp"

  private type Shp = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control) extends StageImpl[Shp](s"$name(${op.name})") {
    val shape: Shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, op)
  }

  private final class Logic(shape: Shp, layer: Layer, op: Op)(implicit ctrl: Control)
    extends Handlers(s"$name(${op.name})", layer, shape) {

    private[this] val hIn       = Handlers.InDMain  (this, shape.in )
    private[this] val hOut      = Handlers.OutDMain (this, shape.out)
    private[this] val realOut   = op.realOutput
    private[this] var carry     = false
    private[this] val carryBuf  = new Array[Double](2)

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    @tailrec
    protected def process(): Unit = {
      val remIn   = hIn.available
      if (remIn == 0) return
      val remOut  = hOut.available
      if (remOut == 0) return

      val numIn = if (carry) (remIn + 1) >> 1 else remIn  >> 1
      if (numIn == 0) { // implies that remIn == 1 && !carry
        carryBuf(0) = hIn.next()
        carry       = true
        return
      }

      val numOut  = if (realOut) remOut else remOut >> 1
      val num     = math.min(numIn, numOut)
      val numC    = num << 1
      val in      = hIn .array
      val inOff   = hIn .offset
      val out     = hOut.array
      val outOff  = hOut.offset

      if (num > 0) {
        if (carry) {
          carryBuf(1) = in(inOff)
          op(carryBuf , 0         , out, outOff                           , 1       )
          op(in       , inOff + 1 , out, outOff + (if (realOut) 1 else 2) , num - 1 )
          carry = false
          hIn.advance(numC - 1)
        } else {
          op(in, inOff, out, outOff, num)
          hIn.advance(numC)
        }
      }

      hOut.advance(if (realOut) num else numC)

      if (hIn.isDone) {
        if (hOut.flush()) completeStage()
        return
      }

      process()
    }
  }
}