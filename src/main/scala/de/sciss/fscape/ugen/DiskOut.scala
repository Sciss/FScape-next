package de.sciss.fscape
package ugen

case class DiskOut(path: String, in: GE) extends UGen.ZeroOut {
  protected def makeSignal: Unit = new module.DiskOut
}
