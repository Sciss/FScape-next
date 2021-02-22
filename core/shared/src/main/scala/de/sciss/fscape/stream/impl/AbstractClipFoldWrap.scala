/*
 *  AbstractClipFoldWrap.scala
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
package impl

import akka.stream.{FanInShape3, Inlet}
import Handlers._

import scala.annotation.tailrec
import scala.math.min

trait AbstractClipFoldWrap[A1, E <: BufElem[A1]] {
  this: Handlers[FanInShape3[E, E, E, E]] =>

  type A = A1

  protected def hIn : InMain  [A, E]
  protected def hLo : InAux   [A, E]
  protected def hHi : InAux   [A, E]
  protected def hOut: OutMain [A, E]

  final protected def onDone(inlet: Inlet[_]): Unit = {
    assert (inlet == hIn.inlet)
    if (hOut.flush()) completeStage()
  }

  protected def run(rem: Int): Unit

  @tailrec
  final protected def process(): Unit = {
    val remIO = min(hIn.available, hOut.available)
    if (remIO == 0) return
    val remLo = hLo.available
    if (remLo == 0) return
    val remHi = hHi.available
    if (remHi == 0) return

    val rem = min(remIO, min(remLo, remHi))
    run(rem)

    if (hIn.isDone) {
      if (hOut.flush()) completeStage()
      return
    }

    process()
  }
}

abstract class AbstractClipFoldWrapI(name: String, layer: Layer, shape: FanInShape3[BufI, BufI, BufI, BufI])(implicit ctrl: Control)
  extends Handlers(name, layer, shape) with AbstractClipFoldWrap[Int, BufI] {

  final protected override val hIn : InIMain  = InIMain  (this, shape.in0)
  final protected override val hLo : InIAux   = InIAux   (this, shape.in1)()
  final protected override val hHi : InIAux   = InIAux   (this, shape.in2)()
  final protected override val hOut: OutIMain = OutIMain (this, shape.out)

  protected def op(inVal: A, loVal: A, hiVal: A): A

  final protected def run(rem: Int): Unit = {
    var i = 0
    while (i < rem) {
      hOut.next(op(hIn.next(), hLo.next(), hHi.next()))
      i += 1
    }
  }
}

abstract class AbstractClipFoldWrapL(name: String, layer: Layer, shape: FanInShape3[BufL, BufL, BufL, BufL])
                                    (implicit ctrl: Control)
  extends Handlers(name, layer, shape) with AbstractClipFoldWrap[Long, BufL] {

  final protected override val hIn : InLMain  = InLMain  (this, shape.in0)
  final protected override val hLo : InLAux   = InLAux   (this, shape.in1)()
  final protected override val hHi : InLAux   = InLAux   (this, shape.in2)()
  final protected override val hOut: OutLMain = OutLMain (this, shape.out)

  protected def op(inVal: A, loVal: A, hiVal: A): A

  final protected def run(rem: Int): Unit = {
    var i = 0
    while (i < rem) {
      hOut.next(op(hIn.next(), hLo.next(), hHi.next()))
      i += 1
    }
  }
}

abstract class AbstractClipFoldWrapD(name: String, layer: Layer, shape: FanInShape3[BufD, BufD, BufD, BufD])
                                    (implicit ctrl: Control)
  extends Handlers(name, layer, shape) with AbstractClipFoldWrap[Double, BufD] {

  final protected override val hIn : InDMain  = InDMain  (this, shape.in0)
  final protected override val hLo : InDAux   = InDAux   (this, shape.in1)()
  final protected override val hHi : InDAux   = InDAux   (this, shape.in2)()
  final protected override val hOut: OutDMain = OutDMain (this, shape.out)

  protected def op(inVal: A, loVal: A, hiVal: A): A

  final protected def run(rem: Int): Unit = {
    var i = 0
    while (i < rem) {
      hOut.next(op(hIn.next(), hLo.next(), hHi.next()))
      i += 1
    }
  }
}