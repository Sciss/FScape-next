/*
 *  Module.scala
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

package de.sciss.fscape

import scala.collection.immutable.{IndexedSeq => Vec}

object Module {
  trait Builder {
    def addUGen(p: UGen): Unit
    def visit[U](ref: AnyRef, init: => U): U
  }

  /** This is analogous to `UGenGraph.Builder` in ScalaCollider. */
  def builder: Builder = ???
}
final case class Module(ugens: Vec[UGen]) {
  def run(): Unit = ???
}