/*
 *  In4Out5Shape.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
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

final case class In4Out5Shape[In0, In1, In2, In3, Out0, Out1, Out2, Out3, Out4](in0 : Inlet [In0 ],
                                                                                in1 : Inlet [In1 ],
                                                                                in2 : Inlet [In2 ],
                                                                                in3 : Inlet [In3 ],
                                                                                out0: Outlet[Out0],
                                                                                out1: Outlet[Out1],
                                                                                out2: Outlet[Out2],
                                                                                out3: Outlet[Out3],
                                                                                out4: Outlet[Out4]
                                                                               )
  extends Shape {

  val inlets : ISeq[Inlet [_]] = Vector(in0, in1, in2, in3)
  val outlets: ISeq[Outlet[_]] = Vector(out0, out1, out2, out3, out4)

  def deepCopy(): In4Out5Shape[In0, In1, In2, In3, Out0, Out1, Out2, Out3, Out4] =
    In4Out5Shape(in0.carbonCopy(), in1.carbonCopy(), in2.carbonCopy(), in3.carbonCopy(),
      out0.carbonCopy(), out1.carbonCopy(), out2.carbonCopy(), out3.carbonCopy(), out4.carbonCopy())

  def copyFromPorts(inlets : ISeq[Inlet [_]],
                    outlets: ISeq[Outlet[_]]): In4Out5Shape[In0, In1, In2, In3, Out0, Out1, Out2, Out3, Out4] = {
    require(inlets.size  == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    In4Out5Shape(
      inlets (0).asInstanceOf[Inlet [In0 ]],
      inlets (1).asInstanceOf[Inlet [In1 ]],
      inlets (2).asInstanceOf[Inlet [In2 ]],
      inlets (3).asInstanceOf[Inlet [In3 ]],
      outlets(0).asInstanceOf[Outlet[Out0]],
      outlets(1).asInstanceOf[Outlet[Out1]],
      outlets(2).asInstanceOf[Outlet[Out2]],
      outlets(3).asInstanceOf[Outlet[Out3]],
      outlets(4).asInstanceOf[Outlet[Out4]]
    )
  }
}