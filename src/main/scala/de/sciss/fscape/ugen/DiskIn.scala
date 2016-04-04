package de.sciss.fscape
package ugen

case class DiskIn(path: String) extends UGen.SingleOut {
  protected def makeSignal: Signal = {
    val p = new module.DiskIn
    p.output
  }
}
