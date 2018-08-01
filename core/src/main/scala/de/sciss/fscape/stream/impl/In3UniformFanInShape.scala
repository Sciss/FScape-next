/*
 *  In3UniformFanInShape.scala
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

  def copyFromPorts(inlets : ISeq[Inlet [_]],
                    outlets: ISeq[Outlet[_]]): In3UniformFanInShape[In0, In1, In2, In3, Out] = {
    require(inlets.size  == this.inlets3.size + 3, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.size == 1, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    In3UniformFanInShape(
      inlets (0).asInstanceOf[          Inlet [In0]],
      inlets (1).asInstanceOf[          Inlet [In1]],
      inlets (2).asInstanceOf[          Inlet [In2]],
      inlets.drop(3).asInstanceOf[ISeq[ Inlet [In3]]],
      outlets(0).asInstanceOf[          Outlet[Out]]
    )
  }
}