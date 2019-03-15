/*
 *  Constant.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import de.sciss.fscape.stream

/** Similar to `GraphStages.SingleSource` but with proper `toString` */
object Constant {
  def apply[A, E >: Null <: BufElem[A]](elem: E)(implicit b: stream.Builder): Outlet[E] = {
    require (elem.size == 1)
    val stage0  = new Stage[A, E](elem)
    val stage   = b.add(stage0)
    stage.out
  }

  private type Shape[A, E >: Null <: BufElem[A]] = SourceShape[E]

  private final class Stage[A, E >: Null <: BufElem[A]](elem: E) extends GraphStage[Shape[A, E]] {
    private val name: String = elem.buf(0).toString

    override def toString: String = name

    val shape: Shape = SourceShape(
      Outlet[E](s"$name.out")
    )

    override def initialAttributes: Attributes = Attributes.name(toString)

    def createLogic(attr: Attributes): GraphStageLogic = new Logic[A, E](shape, name, elem)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], name: String, elem: E)
    extends GraphStageLogic(shape) with OutHandler {

    override def toString: String = name

    def onPull(): Unit = {
      push(shape.out, elem)
      completeStage()
    }
    setHandler(shape.out, this)
  }
}
