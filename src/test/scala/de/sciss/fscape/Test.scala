package de.sciss.fscape

import de.sciss.file._

object Test extends App {
  val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  val g = Graph {
    import graph._
    val in  = DiskIn(path = fIn.path)
    val fft = Real1FFT(in, size = 1024)
    DiskOut(path = fOut.path, in = fft)
  }

  val process = g.expand
  process.run()
}
