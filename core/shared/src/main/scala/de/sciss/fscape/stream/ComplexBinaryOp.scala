/*
 *  ComplexBinaryOp.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec

/** Binary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result.
  */
object ComplexBinaryOp {
  import graph.ComplexBinaryOp.Op

  def apply(op: Op, a: OutD, b: OutD)(implicit builder: Builder): OutD = {
    val stage0  = new Stage(builder.layer, op)
    val stage   = builder.add(stage0)
    builder.connect(a, stage.in0)
    builder.connect(b, stage.in1)
    stage.out
  }

  private final val name = "ComplexBinaryOp"

  private type Shp = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control) extends StageImpl[Shp](s"$name(${op.name})") {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.a" ),
      in1 = InD (s"$name.b" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer, op)
  }

  private final class Logic(shape: Shp, layer: Layer, op: Op)(implicit ctrl: Control)
    extends Handlers(s"$name(${op.name})", layer, shape) {

    private[this] val hInA      = Handlers.InDMain  (this, shape.in0)
    private[this] val hInB      = Handlers.InDMain  (this, shape.in1)
    private[this] val hOut      = Handlers.OutDMain (this, shape.out)
    private[this] var carryA    = false
    private[this] var carryB    = false
    private[this] val carryBufA = new Array[Double](2)
    private[this] val carryBufB = new Array[Double](2)

    protected def onDone(inlet: Inlet[_]): Unit =
      if (hOut.flush()) completeStage()

    @tailrec
    protected def process(): Unit = {
      val remInA  = hInA.available
      if (remInA == 0) return
      val remInB  = hInB.available
      if (remInB == 0) return
      val remOut  = hOut.available
      if (remOut == 0) return

      val numInA = if (carryA) (remInA + 1) >> 1 else remInA >> 1
      if (numInA == 0) { // implies that remInA == 1 && !carryA
        carryBufA(0) = hInA.next()
        carryA = true
      }

      val numInB = if (carryB) (remInB + 1) >> 1 else remInB >> 1
      if (numInB == 0) { // implies that remInB == 1 && !carryB
        carryBufB(0) = hInB.next()
        carryB = true
      }

      val numIn   = math.min(numInA, numInB)
      if (numIn == 0) return

      val numOut  = /*if (realOut) remOut else*/ remOut >> 1
      val num     = math.min(numIn, numOut)
      val numC    = num << 1

      val inA     = hInA.array
      val inOffA  = hInA.offset
      val inB     = hInB.array
      val inOffB  = hInB.offset
      val out     = hOut.array
      val outOff  = hOut.offset

      if (num > 0) {
        if (carryA) {
          if (carryB) {
            carryBufA(1) = inA(inOffA)
            carryBufB(1) = inB(inOffB)
            op(carryBufA, 0         , carryBufB , 0         , out, outOff     , 1  )
            op(inA      , inOffA + 1, inB       , inOffB + 1, out, outOff + 2 , num)
            carryB = false
            hInA.advance(numC - 1)
            hInB.advance(numC - 1)
          } else {
            carryBufA(1) = inA(inOffA)
            op(carryBufA, 0         , inB       , inOffB    , out, outOff     , 1  )
            op(inA      , inOffA + 1, inB       , inOffB + 2, out, outOff + 2 , num)
            hInA.advance(numC - 1)
            hInB.advance(numC)
          }
          carryA = false
        } else if (carryB) {
          carryBufB(1) = inB(inOffB)
          op(inA        , inOffA    , carryBufA , 0         , out, outOff     , 1  )
          op(inA        , inOffA + 2, inB       , inOffB + 1, out, outOff + 2 , num)
          carryB = false
          hInA.advance(numC)
          hInB.advance(numC - 1)
        } else {
          op(inA, inOffA, inB, inOffB, out, outOff, num)
          hInA.advance(numC)
          hInB.advance(numC)
        }
      }

      hOut.advance(/*if (realOut) num0 else*/ numC)

      if (hInA.isDone || hInB.isDone) {
        if (hOut.flush()) completeStage()
        return
      }

      process()
    }
  }
}