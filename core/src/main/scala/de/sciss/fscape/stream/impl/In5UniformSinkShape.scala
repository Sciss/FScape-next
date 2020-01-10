/*
 *  In5UniformSinkShape.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}

/** A generalized Sink shape with five individual inlet and multiple uniform inlets. */
final case class In5UniformSinkShape[In0, In1, In2, In3, In4, In5](in0: Inlet[In0], in1: Inlet[In1], in2: Inlet[In2],
                                                                   in3: Inlet[In3], in4: Inlet[In4],
                                                                   inlets5: ISeq[Inlet[In5]])
  extends Shape {

  def outlets: ISeq[Outlet[_]] = Vector.empty

  val inlets: ISeq[Inlet[_]] = in0 +: in1 +: in2 +: in3 +: in4 +: inlets5

  def deepCopy(): In5UniformSinkShape[In0, In1, In2, In3, In4, In5] =
    In5UniformSinkShape(in0.carbonCopy(), in1.carbonCopy(), in2.carbonCopy(), in3.carbonCopy(), in4.carbonCopy(),
      inlets5.map(_.carbonCopy()))
}