/*
 *  BinaryOp.scala
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
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.FilterIn2Impl

object BinaryOp {
  import graph.BinaryOp.Op

  def apply(op: Op, in1: OutD, in2: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(op)
    val stage   = b.add(stage0)
    b.connect(in1, stage.in0)
    b.connect(in2, stage.in1)
    stage.out
  }

  private final class Stage(op: Op)(implicit ctrl: Control)
    extends GraphStage[FanInShape2[BufD, BufD, BufD]] {

    val shape = new FanInShape2(
      in0 = InD ("BinaryOp.in1"),
      in1 = InD ("BinaryOp.in2"),
      out = OutD("BinaryOp.out")
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(op, shape)
  }

  private final class Logic(op: Op,
                            protected val shape: FanInShape2[BufD, BufD, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with FilterIn2Impl[BufD, BufD, BufD] {

    private[this] var inOff             = 0  // regarding `bufIn`
    private[this] var inRemain          = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var outSent           = true
    private[this] var bVal: Double      = _

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    @inline
    private[this] def shouldRead = inRemain == 0 && canRead

    def process(): Unit = {
      var stateChange = false

      if (shouldRead) {
        readIns()
        inRemain    = bufIn0.size
        inOff       = 0
        stateChange = true
      }

      if (outSent) {
        bufOut        = allocOutBuf()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      val chunk = math.min(inRemain, outRemain)
      if (chunk > 0) {
        var inOffI  = inOff
        var outOffI = outOff
        val aStop   = inOffI + chunk
        val a       = bufIn0.buf
        val b       = if (bufIn1 == null) null else bufIn1.buf
        val out     = bufOut.buf
        val bStop   = if (b == null) 0 else bufIn1.size
        var bv      = bVal
        while (inOffI < aStop) {
          val                 av = a(inOffI)
          if (inOffI < bStop) bv = b(inOffI)
          out(outOffI) = op(av, bv)
          inOffI  += 1
          outOffI += 1
        }
        bVal         = bv
        inOff        = inOffI
        inRemain    -= chunk
        outOff       = outOffI
        outRemain   -= chunk
        stateChange  = true
      }

      val flushOut = inRemain == 0 && isClosed(shape.in0)
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(shape.out, bufOut)
        } else {
          bufOut.release()
        }
        bufOut      = null
        outSent     = true
        stateChange = true
      }

      if      (flushOut && outSent) completeStage()
      else if (stateChange)         process()
    }
  }
}