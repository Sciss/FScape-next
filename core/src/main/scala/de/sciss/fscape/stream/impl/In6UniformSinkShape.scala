/*
 *  In6UniformSinkShape.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}

/** A generalized Sink shape with six individual inlet and multiple uniform inlets. */
final case class In6UniformSinkShape[In0, In1, In2, In3, In4, In5, In6](in0: Inlet[In0], in1: Inlet[In1], in2: Inlet[In2],
                                                                   in3: Inlet[In3], in4: Inlet[In4], in5: Inlet[In5],
                                                                   inlets6: ISeq[Inlet[In6]])
  extends Shape {

  def outlets: ISeq[Outlet[_]] = Vector.empty

  val inlets: ISeq[Inlet[_]] = in0 +: in1 +: in2 +: in3 +: in4 +: in5 +: inlets6

  def deepCopy(): In6UniformSinkShape[In0, In1, In2, In3, In4, In5, In6] =
    In6UniformSinkShape(in0.carbonCopy(), in1.carbonCopy(), in2.carbonCopy(), in3.carbonCopy(), in4.carbonCopy(),
      in5.carbonCopy(),
      inlets6.map(_.carbonCopy()))
}