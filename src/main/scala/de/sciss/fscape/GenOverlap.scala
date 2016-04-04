package de.sciss.fscape

import de.sciss.fscape

object GenOverlap {
  trait Config {
    def windowSize: Int
    def stepSize: Int

  }

  trait Process extends fscape.Process_ {

  }
}
case class GenOverlap(in: GE_, windowSize: GE_, stepSize: GE_) extends GE_ {
  def run(): GenOverlap.Process = ???
}

object ApplyWindow {
  trait Process extends fscape.Process_
}
case class ApplyWindow(in: GE_, window: GE_, windowSize: GE_) extends GE_ {
  def run(): ApplyWindow.Process = ???
}

object Percussion {
  trait Process extends fscape.Process_
}
case class Percussion(in: GE_) extends GE_ {
  def run(): Percussion.Process = ???
}

trait Process_

trait GE_ {
//  def asDemandInt    : Demand[Int]
//  def asDemandBoolean: Demand[Boolean]
//  def run(): Process
}

trait Demand[+A] {

}