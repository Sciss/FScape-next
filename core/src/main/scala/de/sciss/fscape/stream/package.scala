/*
 *  package.scala
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

import akka.NotUsed
import akka.stream.scaladsl.{FlowOps, GraphDSL}
import akka.stream.{Inlet, Outlet}

package object stream {
  type Signal[A] = FlowOps[A, NotUsed]

//  // to-do: `unfold` is unnecessarily inefficient because of producing `Option[Int]`.
//  implicit def constIntSignal   (i: Int   ): Signal[Int]    = Source.repeat(i) // or better `single`?
//  implicit def constDoubleSignal(d: Double): Signal[Double] = Source.repeat(d) // or better `single`?

  type InI                     = Inlet[BufI]
  type InD                     = Inlet[BufD]
  type InL                     = Inlet[BufL]
  type InA                     = Inlet[BufLike]

  @inline
  def  InI(name: String): InI = Inlet[BufI](name)
  @inline
  def  InD(name: String): InD = Inlet[BufD](name)
  @inline
  def  InL(name: String): InL = Inlet[BufL](name)
  @inline
  def  InA(name: String): InA = Inlet[BufLike](name)

  type BufElem[A]               = BufLike { type Elem = A }

  type OutI                     = Outlet[BufI]
  type OutD                     = Outlet[BufD]
  type OutL                     = Outlet[BufL]
  type OutA                     = Outlet[BufLike]
  type OutElem[A]               = Outlet[BufElem[A]]

  @inline
  def  OutI(name: String): OutI = Outlet[BufI](name)
  @inline
  def  OutD(name: String): OutD = Outlet[BufD](name)
  @inline
  def  OutL(name: String): OutL = Outlet[BufL](name)

  type GBuilder = GraphDSL.Builder[NotUsed]

  type StreamInElem[A1, Buf1 >: Null <: BufElem[A1]] = StreamIn { type A = A1; type Buf = Buf1 }
}