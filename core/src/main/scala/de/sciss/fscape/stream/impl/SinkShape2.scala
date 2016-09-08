/*
 *  SinkShape2.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
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
final case class SinkShape2[In0, In1](in0: Inlet[In0], in1: Inlet[In1])
  extends Shape {

  val inlets : ISeq[Inlet [_]] = Vector(in0, in1)
  def outlets: ISeq[Outlet[_]] = Vector.empty

  def deepCopy(): SinkShape2[In0, In1] = SinkShape2(in0.carbonCopy(), in1.carbonCopy())

  def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): SinkShape2[In0, In1] = {
    require(inlets.size == 2, s"number of inlets [${inlets.size}] does not match [2]")
    require(outlets.isEmpty, s"number of outlets [${outlets.size}] does not match [0]")
    SinkShape2(inlets(0).asInstanceOf[Inlet[In0]], inlets(1).asInstanceOf[Inlet[In1]])
  }
}