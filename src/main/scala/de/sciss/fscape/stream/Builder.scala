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