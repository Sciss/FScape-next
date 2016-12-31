/*
 *  LFSaw.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn2DImpl, StageImpl, NodeImpl}

object LFSaw {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "LFSaw"

  private type Shape = FanInShape2[BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- detect constant freq input and use multiplication instead of frame-by-frame addition for phase
  // (cf. Resample)
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with GenChunkImpl[BufD, BufD, Shape]
      with GenIn2DImpl[BufD, BufD] {

    private[this] var incr    : Double = _  // single sample delay
    private[this] var phaseOff: Double = _
    private[this] var phase   : Double = _  // internal state; does not include `phaseOff`
    private[this] var init = true

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      // println(s"LFSaw.processChunk($bufIn0, $chunk)")

      var inOffI    = inOff
      var outOffI   = outOff
      val stop      = inOffI + chunk
      val b0        = if (bufIn0 == null) null else bufIn0.buf
      val b1        = if (bufIn1 == null) null else bufIn1.buf
      val out       = bufOut0.buf
      val stop0     = if (b0 == null) 0 else bufIn0.size
      val stop1     = if (b1 == null) 0 else bufIn1.size

      var incrV     = incr
      var phaseOffV = phaseOff
      var phaseV    = phase

      if (init) {
        incrV     = b0(inOffI) // * 1.0
        phaseOffV = b1(inOffI) % 1.0
        phaseV    = -incrV
        init      = false

      } else {
      }

      while (inOffI < stop) {
        if (inOffI < stop0) incrV     = b0(inOffI) // * 1.0
        if (inOffI < stop1) phaseOffV = b1(inOffI) % 1.0
        val phaseVNew = (phaseV + incrV) % 1.0
        val x         = phaseVNew + phaseOffV
        out(outOffI)  = (x % 1.0) * 2 - 1
        phaseV        = phaseVNew
        inOffI       += 1
        outOffI      += 1
      }
      incr      = incrV
      phaseOff  = phaseOffV
      phase     = phaseV
    }
  }
}