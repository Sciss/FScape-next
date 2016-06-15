/*
 *  SinOsc.scala
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
package stream

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn2Impl}

object SinOsc {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape2[BufD, BufD, BufD]] {

    val name = "SinOsc"

    override def toString = s"$name@${hashCode.toHexString}"

    val shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(protected val shape: FanInShape2[BufD, BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with GenChunkImpl[BufD, BufD, FanInShape2[BufD, BufD, BufD]]
      with GenIn2Impl                          [BufD, BufD, BufD] {

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    protected def in0: Inlet [BufD] = shape.in0
    protected def out: Outlet[BufD] = shape.out

    private[this] var incr    : Double = _  // single sample delay
    private[this] var phaseOff: Double = _
    private[this] var phase   : Double = _  // internal state; does not include `phaseOff`
    private[this] var init = true

    import Util.Pi2

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Int = {
      // println(s"SinOsc.processChunk($bufIn0, $chunk)")

      var inOffI    = inOff
      var outOffI   = outOff
      val stop      = inOffI + chunk
      val b0        = if (bufIn0 == null) null else bufIn0.buf
      val b1        = if (bufIn1 == null) null else bufIn1.buf
      val out       = bufOut.buf
      val stop0     = if (b0 == null) 0 else bufIn0.size
      val stop1     = if (b1 == null) 0 else bufIn1.size

      var incrV     = incr
      var phaseOffV = phaseOff
      var phaseV    = phase

      if (init) {
        incrV     = b0(inOffI) * Pi2
        phaseOffV = b1(inOffI) % Pi2
        phaseV    = -incrV
        init      = false

      } else {
      }

      while (inOffI < stop) {
        if (inOffI < stop0) incrV     = b0(inOffI) * Pi2
        if (inOffI < stop1) phaseOffV = b1(inOffI) % Pi2
        val phaseVNew = (phaseV + incrV) % Pi2
        val x         = phaseVNew + phaseOffV
        out(outOffI)  = math.sin(x)
        phaseV        = phaseVNew
        inOffI       += 1
        outOffI      += 1
      }
      incr      = incrV
      phaseOff  = phaseOffV
      phase     = phaseV
      chunk
    }
  }
}