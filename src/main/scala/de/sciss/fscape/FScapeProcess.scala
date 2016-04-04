package de.sciss.fscape

import scala.collection.immutable.{IndexedSeq => Vec}

object FScapeProcess {
  trait Builder {
    def addModule(p: Module): Unit
    def visit[U](ref: AnyRef, init: => U): U
  }

  /** This is analogous to `UGenGraph.Builder` in ScalaCollider. */
  def builder: Builder = ???
}
final case class FScapeProcess(modules: Vec[Module]) {
  def run(): Unit = ???
}