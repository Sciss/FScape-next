/*
 *  Builder.scala
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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.{Graph, Inlet, Outlet, Shape}

object Builder {
  def apply()(implicit dsl: GraphDSL.Builder[NotUsed], ctrl: Control): Settable = new Impl(ctrl)

  trait Settable extends Builder {
    def layer_=(value: Int): Unit
  }

  private final class Impl(val control: Control)(implicit b: GraphDSL.Builder[NotUsed]) extends Settable {
    var layer = 0

    def add[S <: Shape](graph: Graph[S, _]): S = b.add(graph)

    def dsl: GraphDSL.Builder[NotUsed] = b

    def connect[A](out: Outlet[A], in: Inlet[A]): Unit = {
      import GraphDSL.Implicits._
      out ~> in
    }

    def map[A, B](out: Outlet[A], name: String)(fun: A => B): Outlet[B] = {
      Map(out, name)(fun)(this)
//      import GraphDSL.Implicits._
//      out.map(fun).outlet
    }
  }
}
trait Builder {
  def control: Control

  def layer: Layer

  def dsl: GraphDSL.Builder[NotUsed]

  def add[S <: Shape](graph: Graph[S, _]): S

  def map[A, B](out: Outlet[A], name: String)(fun: A => B): Outlet[B]

  def connect[A](out: Outlet[A], in: Inlet[A]): Unit
}