/*
 *  BiformFanInShape.scala
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

// with single output
case class BiformFanInShape[I1, I2, O](ins1: ISeq[Inlet[I1]], ins2: ISeq[Inlet[I2]], out: Outlet[O])
  extends Shape {

  val inlets: ISeq[Inlet[_]] = ins1 ++ ins2

  val outlets: ISeq[Outlet[_]] = out :: Nil

  override def deepCopy(): BiformFanInShape[I1, I2, O] =
    BiformFanInShape(ins1.map(_.carbonCopy()), ins2.map(_.carbonCopy()), out.carbonCopy())
}

// with multiple outputs
case class BiformShape[I1, I2, O](ins1: ISeq[Inlet[I1]], ins2: ISeq[Inlet[I2]], outlets: ISeq[Outlet[O]])
  extends Shape {

  val inlets: ISeq[Inlet[_]] = ins1 ++ ins2

  override def deepCopy(): BiformShape[I1, I2, O] =
    BiformShape(ins1.map(_.carbonCopy()), ins2.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
}
