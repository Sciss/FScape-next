/*
 *  In6Out3Shape.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
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

final case class In6Out3Shape[In0, In1, In2, In3, In4, In5, Out0, Out1, Out2](in0 : Inlet [In0 ],
                                                                              in1 : Inlet [In1 ],
                                                                              in2 : Inlet [In2 ],
                                                                              in3 : Inlet [In3 ],
                                                                              in4 : Inlet [In4 ],
                                                                              in5 : Inlet [In5 ],
                                                                              out0: Outlet[Out0],
                                                                              out1: Outlet[Out1],
                                                                              out2: Outlet[Out2])
  extends Shape {

  val inlets : ISeq[Inlet [_]] = Vector(in0, in1, in2, in3, in4, in5)
  val outlets: ISeq[Outlet[_]] = Vector(out0, out1, out2)

  def deepCopy(): In6Out3Shape[In0, In1, In2, In3, In4, In5, Out0, Out1, Out2] =
    In6Out3Shape(in0.carbonCopy(), in1.carbonCopy(), in2.carbonCopy(), in3.carbonCopy(), in4.carbonCopy(),
      in5.carbonCopy(), out0.carbonCopy(), out1.carbonCopy(), out2.carbonCopy())
}