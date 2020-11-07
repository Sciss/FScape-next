package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object MixMonoEqPTest extends App {
  val ctrl = stream.Control()

  lazy val g = Graph {
    import graph._
    val one     = DC(1.0).take(1)
    val two     = DC(2.0).take(1)
    val three   = DC(3.0).take(1)
    val oneTwo  = Seq(one, two): GE
    val all     = Seq(one, two, three): GE

    Mix.MonoEqP(one   ).poll(0, "one-only")
    Mix.MonoEqP(two   ).poll(0, "two-only")
    Mix.MonoEqP(oneTwo).poll(0, "1+2   (3 / sqrt(2) = 2.121)")
    Mix.MonoEqP(all   ).poll(0, "1+2+3 (6 / sqrt(3) = 3.464)")
  }

  ctrl.run(g)
  println("Running.")
  Await.result(ctrl.status, Duration.Inf)
  println("Done.")
  sys.exit()
}