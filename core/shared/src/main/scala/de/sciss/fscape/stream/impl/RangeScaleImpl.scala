package de.sciss.fscape.stream.impl

import akka.stream.{FanInShape5, Inlet}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, OutDMain}
import de.sciss.fscape.stream.{BufD, Control, Layer}

import scala.annotation.tailrec
import scala.math.min

abstract class RangeScaleImpl(name: String, layer: Layer, shape: FanInShape5[BufD, BufD, BufD, BufD, BufD, BufD])
                             (implicit ctrl: Control)
  extends Handlers(name, layer, shape) {

  private[this] val hIn     = InDMain  (this, shape.in0)
  private[this] val hOut    = OutDMain (this, shape.out)
  private[this] val hInLo   = InDAux   (this, shape.in1)()
  private[this] val hInHi   = InDAux   (this, shape.in2)()
  private[this] val hOutLo  = InDAux   (this, shape.in3)()
  private[this] val hOutHi  = InDAux   (this, shape.in4)()

  protected def onDone(inlet: Inlet[_]): Unit =
    if (hOut.flush()) completeStage()

  protected def run(in: Array[Double], inOff0: Int, out: Array[Double], outOff0: Int, num: Int,
                    hInLo: InDAux, hInHi: InDAux, hOutLo: InDAux, hOutHi: InDAux): Unit

  @tailrec
  final protected def process(): Unit = {
    val remIO = min(hIn.available, hOut.available)
    if (remIO == 0) return
    val remInLo   = hInLo .available
    if (remInLo == 0) return
    val remInHi   = hInHi .available
    if (remInHi == 0) return
    val remOutLo  = hOutLo.available
    if (remOutLo == 0) return
    val remOutHi  = hOutHi.available
    if (remOutHi == 0) return

    val rem     = min(remIO, min(remInLo, min(remInHi, min(remOutLo, remOutHi))))
    val in      = hIn.array
    val inOff   = hIn.offset
    val out     = hOut.array
    val outOff  = hOut.offset

    run(in, inOff, out, outOff, rem, hInLo, hInHi, hOutLo, hOutHi)

    hIn .advance(rem)
    hOut.advance(rem)

    if (hIn.isDone) {
      if (hOut.flush()) completeStage()
      return
    }

    process()
  }
}