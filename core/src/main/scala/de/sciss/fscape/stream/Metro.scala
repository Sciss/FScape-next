/*
 *  Metro.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{DemandGenIn2, DemandWindowedLogic, NodeImpl, StageImpl}

object Metro {
  def apply(period: OutL, phase: OutL)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(period, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "Metro"

  private type Shape = FanInShape2[BufL, BufL, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InL (s"$name.period"),
      in1 = InL (s"$name.phase"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with DemandGenIn2[BufL, BufL, BufI]
      with DemandWindowedLogic[Shape] {

    private[this] var period  : Long  = _
    private[this] var phaseOff: Long  = _
    private[this] var phase   : Long  = _    // internal state; does not include `phaseOff`
//    private[this] var init            = true
    private[this] var _inputsEnded    = false

    protected def inputsEnded: Boolean = _inputsEnded   // never or when period == 0

    protected def startNextWindow(): Long = {
      val inOff = auxInOff
      if (bufIn0 != null && inOff < bufIn0.size) {
        period = math.max(0, bufIn0.buf(inOff))
//        println(s"PERIOD[$inOff] = $period")
        if (period == 0L) {
          _inputsEnded  = true  // XXX TODO --- ugly trick to work with infinite window size
          period        = 0x3fffffffffffffffL // Long.MaxValue
        }
      }
      if (/*init &&*/ bufIn1 != null && inOff < bufIn1.size) {
        phaseOff = bufIn1.buf(inOff)
        // init = false
      }

      val _period   = period
      phase         = (phaseOff + _period - 1) % _period + 1
      period
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && {
      isClosed(in0) && isClosed(in1)
    })

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = ()

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
//      _shape.fill(winSize = winSize, winOff = readFromWinOff, buf = bufOut0.buf, bufOff = outOff,
//        len = chunk, param = param)
      val out       = bufOut0.buf
      var outOffI   = outOff
      val stop      = outOffI + chunk

      val periodV   = period
      var phaseV    = phase

      while (outOffI < stop /*inOffI < stop*/) {
        if (phaseV >= periodV) {
          phaseV %= periodV
          out(outOffI)  = 1
        } else {
          out(outOffI)  = 0
        }
        phaseV       += 1
        outOffI      += 1
      }
//      period    = periodV
      phase     = phaseV
    }

    protected def processWindow(writeToWinOff: Long): Long = writeToWinOff

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

//    private def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
//      // println(s"Metro.processChunk($bufIn0, $chunk)")
//
//      var inOffI    = inOff
//      var outOffI   = outOff
//      val stop      = inOffI + chunk
//      val b0        = if (bufIn0 == null) null else bufIn0.buf
//      val out       = bufOut0.buf
//      val stop0     = if (b0 == null) 0 else bufIn0.size
//
//      var periodV   = period
//      var phaseV    = phase
//
//      if (init) {
//        val b1        = bufIn1.buf
//        periodV       = b0(inOffI)
//        if (periodV == 0) periodV = Long.MaxValue
//        val phaseOffV = b1(inOffI)
//        phaseV        = (phaseOffV + periodV - 1) % periodV + 1
//        init          = false
//      }
//
//      while (inOffI < stop) {
//        if (inOffI < stop0) {
//          periodV = b0(inOffI)
//          if (periodV == 0) periodV = Long.MaxValue
//        }
//        if (phaseV >= periodV) {
//          phaseV %= periodV
//          out(outOffI)  = 1
//        } else {
//          out(outOffI)  = 0
//        }
//        phaseV       += 1
//        inOffI       += 1
//        outOffI      += 1
//      }
//      period    = periodV
//      phase     = phaseV
//    }
  }
}