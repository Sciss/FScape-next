/*
 *  GeomSeq.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{AbstractSeqGen, StageImpl}

object GeomSeq {
  def apply[A, E >: Null <: BufElem[A]](start: Outlet[E], grow: Outlet[E], length: OutL)
                                       (implicit b: Builder, tpe: StreamType[A, E],
                                        num: Numeric[A]): Outlet[E] = {
    val stage0  = new Stage[A, E]
    val stage   = b.add(stage0)
    b.connect(start , stage.in0)
    b.connect(grow  , stage.in1)
    b.connect(length, stage.in2)
    stage.out
  }

  private final val name = "GeomSeq"

  private type Shape[A, E >: Null <: BufElem[A]] = FanInShape3[E, E, BufL, E]

  private final class Stage[A, E >: Null <: BufElem[A]](implicit ctrl: Control, tpe: StreamType[A, E],
                                                        num: Numeric[A])
    extends StageImpl[Shape[A, E]](name) {

    val shape = new FanInShape3(
      in0 = Inlet[E] (s"$name.start" ),
      in1 = Inlet[E] (s"$name.grow"  ),
      in2 = InL      (s"$name.length"),
      out = Outlet[E](s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E])
                                                       (implicit ctrl: Control, tpe: StreamType[A, E],
                                                        num: Numeric[A])
    extends AbstractSeqGen[A, E](name, shape) {

    protected def inc(a: A, b: A): A = num.times(a, b)
  }
}