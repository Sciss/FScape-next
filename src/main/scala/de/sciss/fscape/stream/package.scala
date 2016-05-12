package de.sciss.fscape

import akka.NotUsed
import akka.stream.scaladsl.{FlowOps, Source}

import scala.language.implicitConversions

package object stream {
  type Signal[A] = FlowOps[A, NotUsed]

  // to-do: `unfold` is unnecessarily inefficient because of producing `Option[Int]`.
  implicit def constIntSignal   (i: Int   ): Signal[Int]    = Source.repeat(i) // or better `single`?
  implicit def constDoubleSignal(d: Double): Signal[Double] = Source.repeat(d) // or better `single`?
}