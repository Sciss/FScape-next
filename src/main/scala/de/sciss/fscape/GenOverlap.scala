package de.sciss.fscape

import java.io.File

import de.sciss.fscape
import de.sciss.synth.io.AudioFileSpec

object GenOverlap {
  trait Config {
    def windowSize: Int
    def stepSize: Int

  }

  trait Process extends fscape.Process_ {

  }
}
case class GenOverlap(in: GE, windowSize: GE, stepSize: GE) extends GE {
  def run(): GenOverlap.Process = ???
}

object ApplyWindow {
  trait Process extends fscape.Process_
}
case class ApplyWindow(in: GE, window: GE, windowSize: GE) extends GE {
  def run(): ApplyWindow.Process = ???
}

//object FFT {
//  trait Process extends fscape.Process
//}
//case class FFT(in: GE, fftSize: GE) extends GE {
//  def run(): FFT.Process = ???
//}

object WriteFile {
  trait Process extends fscape.Process_
}
case class WriteFile(in: GE, out: File, spec: AudioFileSpec) {
  def run(): WriteFile.Process = ???
}

object Percussion {
  trait Process extends fscape.Process_
}
case class Percussion(in: GE) extends GE {
  def run(): Percussion.Process = ???
}

trait Process_

trait GE {
//  def asDemandInt    : Demand[Int]
//  def asDemandBoolean: Demand[Boolean]
//  def run(): Process
}

trait Demand[+A] {

}