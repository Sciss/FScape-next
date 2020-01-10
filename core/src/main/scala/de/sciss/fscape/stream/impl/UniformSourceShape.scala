/*
 *  UniformSourceShape.scala
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

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}

/** A generalized Source shape with multiple uniform outlets. */
final case class UniformSourceShape[Out](outlets: ISeq[Outlet[Out]])
  extends Shape {

  def inlets: ISeq[Inlet[_]] = Vector.empty

  def deepCopy(): UniformSourceShape[Out] =
    UniformSourceShape(outlets.map(_.carbonCopy()))

  def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): UniformSourceShape[Out] = {
    require(inlets.isEmpty, s"number of inlets [${inlets.size}] does not match [0]")
    require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    UniformSourceShape(outlets.asInstanceOf[ISeq[Outlet[Out]]])
  }

  val outArray: Array[Outlet[Out]] = outlets.toArray
  def out(n: Int): Outlet[Out] = outArray(n)
}