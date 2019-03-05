/*
 *  In1UniformSinkShape.scala
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

package de.sciss.fscape
package stream
package impl

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}
import scala.language.existentials

/** A generalized Sink shape with one individual inlet and multiple uniform inlets. */
final case class In1UniformSinkShape[In0, In1](in0: Inlet[In0], inlets1: ISeq[Inlet[In1]])
  extends Shape {

  def outlets: ISeq[Outlet[_]] = Vector.empty

  val inlets: ISeq[Inlet[_]] = in0 +: inlets1

  def deepCopy(): In1UniformSinkShape[In0, In1] =
    In1UniformSinkShape(in0.carbonCopy(), inlets1.map(_.carbonCopy()))

  def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): In1UniformSinkShape[In0, In1] = {
    require(inlets.size == this.inlets1.size + 1, s"number of inlets [${inlets.size}] does not match [${this.inlets1.size + 1}]")
    require(outlets.isEmpty, s"number of outlets [${outlets.size}] does not match [0]")
    val ISeq(in0c, in1c @ _*) = inlets
    In1UniformSinkShape(in0c.asInstanceOf[Inlet[In0]], in1c.asInstanceOf[ISeq[Inlet[In1]]])
  }
}