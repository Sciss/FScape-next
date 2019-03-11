/*
 *  UniformSinkShape.scala
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

/** A generalized Sink shape with multiple uniform inlets. */
final case class UniformSinkShape[In](inlets: ISeq[Inlet[In]])
  extends Shape {

  def outlets: ISeq[Outlet[_]] = Vector.empty

  def deepCopy(): UniformSinkShape[In] =
    UniformSinkShape(inlets.map(_.carbonCopy()))
}