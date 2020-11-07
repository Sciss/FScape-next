package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._

object DropWhileTest extends App {
  val g = Graph {
    import graph._
    val saw = LFSaw(freqN = 1.0/100, phase = 0.5)
    val tk  = saw.take(200)
    val dw  = tk.dropWhile(tk >= 0)
    Length(dw).poll(0, "drop-length (should be 150)")
    val tw  = tk.takeWhile(tk >= 0)
    Length(tw).poll(0, "take-length (should be 50)")
  }

  stream.Control().run(g)
}