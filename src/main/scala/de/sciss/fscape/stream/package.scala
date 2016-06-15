/*
 *  package.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import akka.NotUsed
import akka.stream.scaladsl.{FlowOps, GraphDSL}
import akka.stream.{Inlet, Outlet}

import scala.language.implicitConversions

package object stream {
  type Signal[A] = FlowOps[A, NotUsed]

//  // to-do: `unfold` is unnecessarily inefficient because of producing `Option[Int]`.
//  implicit def constIntSignal   (i: Int   ): Signal[Int]    = Source.repeat(i) // or better `single`?
//  implicit def constDoubleSignal(d: Double): Signal[Double] = Source.repeat(d) // or better `single`?

  type InI                     = Inlet[BufI]
  type InD                     = Inlet[BufD]

  @inline
  def  InI(name: String): InI = Inlet[BufI](name)
  @inline
  def  InD(name: String): InD = Inlet[BufD](name)

  type OutI                     = Outlet[BufI]
  type OutD                     = Outlet[BufD]

  @inline
  def  OutI(name: String): OutI = Outlet[BufI](name)
  @inline
  def  OutD(name: String): OutD = Outlet[BufD](name)

  type GBuilder = GraphDSL.Builder[NotUsed]
}