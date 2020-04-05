/*
 *  SeqGenLogic.scala
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

package de.sciss.fscape.stream
package impl

import akka.stream.{FanInShape3, Inlet}
import de.sciss.fscape.stream.impl.Handlers._

import scala.annotation.tailrec

final class SeqGenLogicI(name: String, shape: FanInShape3[BufI, BufI, BufL, BufI], layer: Layer)
                        (inc: (Int, Int) => Int)
                        (implicit ctrl: Control)
  extends SeqGenLogic[Int, BufI](name, shape, layer)  {

  protected override val hStep : InIAux    = InIAux  (this, shape.in1)()
  protected override val hOut  : OutIMain  = OutIMain(this, shape.out)
  
  protected def run(x: Int, n: Int): Int = {
    var _x = x
    var i = 0
    while (i < n) {
      hOut.next(_x)
      val _step = hStep.next()
      _x = inc(_x, _step)
      i += 1
    }
    _x
  }
}

final class SeqGenLogicL(name: String, shape: FanInShape3[BufL, BufL, BufL, BufL], layer: Layer)
                        (inc: (Long, Long) => Long)
                        (implicit ctrl: Control)
  extends SeqGenLogic[Long, BufL](name, shape, layer)  {

  protected override val hStep : InLAux    = InLAux  (this, shape.in1)()
  protected override val hOut  : OutLMain  = OutLMain(this, shape.out)

  protected def run(x: Long, n: Int): Long = {
    var _x = x
    var i = 0
    while (i < n) {
      hOut.next(_x)
      val _step = hStep.next()
      _x = inc(_x, _step)
      i += 1
    }
    _x
  }
}

final class SeqGenLogicD(name: String, shape: FanInShape3[BufD, BufD, BufL, BufD], layer: Layer)
                        (inc: (Double, Double) => Double)
                        (implicit ctrl: Control)
  extends SeqGenLogic[Double, BufD](name, shape, layer)  {

  protected override val hStep : InDAux    = InDAux  (this, shape.in1)()
  protected override val hOut  : OutDMain  = OutDMain(this, shape.out)

  protected def run(x: Double, n: Int): Double = {
    var _x = x
    var i = 0
    while (i < n) {
      hOut.next(_x)
      val _step = hStep.next()
      _x = inc(_x, _step)
      i += 1
    }
    _x
  }
}

abstract class SeqGenLogic[A, E >: Null <: BufElem[A]](name: String, shape: FanInShape3[E, E, BufL, E],
                                                    layer: Layer)
                                                   (implicit ctrl: Control, val tpe: StreamType[A, E])
  extends Handlers(name, layer, shape)  {

  // ---- impl ----

  private[this] val hStart: InAux   [A, E]  = InAux   [A, E](this, shape.in0)()
  private[this] val hLen  : InLAux          = InLAux        (this, shape.in2)(math.max(0L, _))

  protected val hOut  : OutMain [A, E]
  protected val hStep : InAux   [A, E]

  private[this] var init = true

  private[this] var x     : A  = _
  private[this] var remain: Long    = _

  final protected def onDone(inlet: Inlet[_]): Unit = assert(false)

  protected def run(x: A, n: Int): A

  @tailrec
  final protected def process(): Unit = {
    if (init) {
      if (hLen.isConstant) {
        if (hOut.flush()) completeStage()
        return
      }
      if (!(hLen.hasNext && hStart.hasNext)) return
      remain  = hLen  .next()
      x       = hStart.next()
      init    = false
    }

    val rem = math.min(math.min(hOut.available, hStep.available), remain).toInt
    if (rem > 0) {
      x       = run(x, rem)
      remain -= rem
    }

    if (remain == 0L) {
      init = true
      process()
    }
  }
}
