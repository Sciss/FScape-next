package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ElasticTest extends App {
  val n: Int = 32

  lazy val g = Graph {
    import graph._
    val in  = ArithmSeq(length = 3 * n) % 16
    val red = ReduceWindow.max(in, 3 * n)
    val rep = RepeatWindow(red, 1, 3 * n)
    val dif = rep - in.elastic()
    dif.poll(1, "dif")
    Length(dif).poll("len")
  }

  val config = Control.Config()
  config.blockSize = n
  var gui: SimpleGUI = _
  implicit val ctrl: Control = Control(config)

  ctrl.run(g)
  Await.result(ctrl.status, Duration.Inf)
}