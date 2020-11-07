package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._

object LineTest extends App {
  val g = Graph {
    import graph._
    val gen = Line(33, 44, 44 - 33 + 1)
    gen.head.poll(0, "head")
    gen.last.poll(0, "last")
    RepeatWindow(gen).poll(Metro(2), "iter")
    Length(gen).poll(0, "len")
  }

  stream.Control().run(g)
}