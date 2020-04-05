/*
 *  SinkShape2.scala
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

package de.sciss.fscape.stream.impl.shapes

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}

/** A generalized Sink shape with multiple uniform inlets. */
final case class SinkShape2[In0, In1](in0: Inlet[In0], in1: Inlet[In1])
  extends Shape {

  val inlets : ISeq[Inlet [_]] = Vector(in0, in1)
  def outlets: ISeq[Outlet[_]] = Vector.empty

  def deepCopy(): SinkShape2[In0, In1] = SinkShape2(in0.carbonCopy(), in1.carbonCopy())
}