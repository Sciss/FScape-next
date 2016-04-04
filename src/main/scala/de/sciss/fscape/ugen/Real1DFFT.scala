package de.sciss.fscape
package ugen

/**
  *
  * @param in       the input signal to transform
  * @param size     the size of chunks to take from the input
  * @param padding  the number of frames to pad chunks with before transforming.
  *                 thus fft size = size + padding
  */
case class Real1DFFT(in: GE, size: GE, padding: GE = 0) extends UGen.SingleOut {
  protected def makeSignal: Signal = {
    val p = new module.Real1DFFT
    p.output
  }
}
