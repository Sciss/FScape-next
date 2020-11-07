package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._
import de.sciss.fscape.stream.Control

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object LengthBufferDiskTest extends App {
  import graph._

  lazy val g = Graph {
    val sz = 17000

    val in        = SinOsc(freqN = 1.0/16 /* 4410/sr */, phase = 0 /* math.Pi/2 */).take(sz)
    val max       = RunningMax(in).last
    val buf       = BufferDisk(in)
    val sig       = buf * max
    Length(sig).poll(0, "len")
  }

  val config = Control.Config()
//  var gui: SimpleGUI = _
//  config.progressReporter = rep => Swing.onEDT(gui.progress = rep.total)
//  config.useAsync = false   // bug only in async mode
  val ctrl = Control(config)

//  Swing.onEDT {
//    gui = SimpleGUI(ctrl)
//  }

//  showStreamLog = true
  ctrl.run(g)
  println("Running.")
  Await.result(ctrl.status, Duration.Inf)

}