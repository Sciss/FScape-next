/*
 *  In3UniformFanInShape.scala
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
package stream
package impl

import akka.stream.{Inlet, Outlet, Shape}

import scala.collection.immutable.{Seq => ISeq}

/** A fan shape with three distinct and one multiple uniform inlet. */
final case class In3UniformFanInShape[In0, In1, In2, In3, Out](in0    :      Inlet [In0],
                                                               in1    :      Inlet [In1],
                                                               in2    :      Inlet [In2],
                                                               inlets3: ISeq[Inlet [In3]],
                                                               out    :      Outlet[Out])
  extends Shape {

  val inlets : ISeq[Inlet [_]] = in0 +: in1 +: in2 +: inlets3
  val outlets: ISeq[Outlet[_]] = out :: Nil

  def deepCopy(): In3UniformFanInShape[In0, In1, In2, In3, Out] =
    In3UniformFanInShape(in0.carbonCopy(), in1.carbonCopy(), in2.carbonCopy(), inlets3.map(_.carbonCopy()),
      out.carbonCopy())
}