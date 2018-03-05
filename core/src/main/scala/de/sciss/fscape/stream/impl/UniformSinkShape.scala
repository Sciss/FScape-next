/*
 *  UniformSinkShape.scala
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
package impl

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}

/** A generalized Sink shape with multiple uniform inlets. */
final case class UniformSinkShape[In](inlets: ISeq[Inlet[In]])
  extends Shape {

  def outlets: ISeq[Outlet[_]] = Vector.empty

  def deepCopy(): UniformSinkShape[In] =
    UniformSinkShape(inlets.map(_.carbonCopy()))

  def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): UniformSinkShape[In] = {
    require(inlets.size == this.inlets.size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.isEmpty, s"number of outlets [${outlets.size}] does not match [0]")
    UniformSinkShape(inlets.asInstanceOf[ISeq[Inlet[In]]])
  }

  val inArray: Array[Inlet[In]] = inlets.toArray
  def in(n: Int): Inlet[In] = inArray(n)
}