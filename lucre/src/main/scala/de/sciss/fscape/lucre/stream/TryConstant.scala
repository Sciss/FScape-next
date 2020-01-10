/*
 *  TryConstant.scala
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
package lucre.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import de.sciss.fscape.stream._
import de.sciss.fscape.stream.impl.NodeImpl

import scala.util.{Failure, Success, Try}

object TryConstant {
  def apply[A, E >: Null <: BufElem[A]](elemTr: Try[E])(implicit b: stream.Builder): Outlet[E] = {
    val stage0  = new Stage[A, E](elemTr, b.layer)
    val stage   = b.add(stage0)
    stage.out
  }

  private type Shape[A, E >: Null <: BufElem[A]] = SourceShape[E]

  private final class Stage[A, E >: Null <: BufElem[A]](elemTr: Try[E], layer: Layer)(implicit control: Control)
    extends GraphStage[Shape[A, E]] {

    private val name: String = elemTr match {
      case Success(e)   => e.buf(0).toString
      case Failure(ex)  => s"${ex.getClass}(${ex.getMessage})"
    }

    override def toString: String = name

    val shape: Shape = SourceShape(
      Outlet[E](s"$name.out")
    )

    override def initialAttributes: Attributes = Attributes.name(toString)

    def createLogic(attr: Attributes): GraphStageLogic = new Logic[A, E](shape, name, layer, elemTr)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], name: String, layer: Layer, elemTr: Try[E])
                                                       (implicit control: Control)
    extends NodeImpl(name, layer, shape) with OutHandler {

    override def toString: String = name

    def onPull(): Unit = elemTr match {
      case Success(elem) =>
        push(shape.out, elem)
        completeStage()
      case Failure(ex) =>
        notifyFail(ex)
    }

    setHandler(shape.out, this)
  }
}