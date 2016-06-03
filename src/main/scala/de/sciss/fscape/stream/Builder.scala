/*
 *  Builder.scala
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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.{Graph, Inlet, Outlet, Shape}

object Builder {
  def apply()(implicit dsl: GraphDSL.Builder[NotUsed], ctrl: Control): Builder = new Impl(ctrl)

  private final class Impl(val control: Control)(implicit b: GraphDSL.Builder[NotUsed]) extends Builder {
    def add[S <: Shape](graph: Graph[S, _]): S = b.add(graph)

    def connect[A](out: Outlet[A], in: Inlet[A]): Unit = {
      import GraphDSL.Implicits._
      out ~> in
    }
  }
}
trait Builder {
  def control: Control

  def add[S <: Shape](graph: Graph[S, _]): S

  def connect[A](out: Outlet[A], in: Inlet[A]): Unit
}