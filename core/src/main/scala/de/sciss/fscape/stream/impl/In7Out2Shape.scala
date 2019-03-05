/*
 *  In7Out2Shape.scala
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

final case class In7Out2Shape[In0, In1, In2, In3, In4, In5, In6, Out0, Out1](in0 : Inlet [In0 ],
                                                                             in1 : Inlet [In1 ],
                                                                             in2 : Inlet [In2 ],
                                                                             in3 : Inlet [In3 ],
                                                                             in4 : Inlet [In4 ],
                                                                             in5 : Inlet [In5 ],
                                                                             in6 : Inlet [In6 ],
                                                                             out0: Outlet[Out0],
                                                                             out1: Outlet[Out1])
  extends Shape {

  val inlets : ISeq[Inlet [_]] = Vector(in0, in1, in2, in3, in4, in5, in6)
  val outlets: ISeq[Outlet[_]] = Vector(out0, out1)

  def deepCopy(): In7Out2Shape[In0, In1, In2, In3, In4, In5, In6, Out0, Out1] =
    In7Out2Shape(in0.carbonCopy(), in1.carbonCopy(), in2.carbonCopy(), in3.carbonCopy(), in4.carbonCopy(),
      in5.carbonCopy(), in6.carbonCopy(), out0.carbonCopy(), out1.carbonCopy())

  def copyFromPorts(inlets : ISeq[Inlet [_]],
                    outlets: ISeq[Outlet[_]]): In7Out2Shape[In0, In1, In2, In3, In4, In5, In6, Out0, Out1] = {
    require(inlets.size  == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    In7Out2Shape(
      inlets (0).asInstanceOf[Inlet [In0 ]],
      inlets (1).asInstanceOf[Inlet [In1 ]],
      inlets (2).asInstanceOf[Inlet [In2 ]],
      inlets (3).asInstanceOf[Inlet [In3 ]],
      inlets (4).asInstanceOf[Inlet [In4 ]],
      inlets (5).asInstanceOf[Inlet [In5 ]],
      inlets (6).asInstanceOf[Inlet [In6 ]],
      outlets(0).asInstanceOf[Outlet[Out0]],
      outlets(1).asInstanceOf[Outlet[Out1]]
    )
  }
}