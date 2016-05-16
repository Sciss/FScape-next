/*
 *  Real1FFT.scala
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

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.FilterIn3Impl
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

import scala.annotation.tailrec

object Real1FFT {
  def apply(in: Outlet[BufD], size: Outlet[BufI], padding: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Impl(ctrl)
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in      ~> stage.in0
    size    ~> stage.in1
    padding ~> stage.in2

    stage.out
  }

  private final class Impl(ctrl: Control) extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {
    val shape = new FanInShape3(
      in0 = Inlet [BufD]("Real1FFT.in"     ),
      in1 = Inlet [BufI]("Real1FFT.size"   ),
      in2 = Inlet [BufI]("Real1FFT.padding"),
      out = Outlet[BufD]("Real1FFT.out"    )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape, ctrl)

    private final class Logic(protected val shape: FanInShape3[BufD, BufI, BufI, BufD],
                              protected val ctrl: Control)
      extends GraphStageLogic(shape) with FilterIn3Impl[BufD, BufI, BufI, BufD] {

      private[this] var fft       : DoubleFFT_1D  = _
      private[this] var fftBuf    : Array[Double] = _

      private[this] var size      : Int  = _
      private[this] var padding   : Int  = _

      private[this] var fftInOff      = 0  // regarding `fftBuf`
      private[this] var fftInRemain   = 0
      private[this] var fftOutOff     = 0  // regarding `fftBuf`
      private[this] var fftOutRemain  = 0
      private[this] var inOff         = 0  // regarding `bufIn`
      private[this] var inRemain      = 0
      private[this] var outOff        = 0  // regarding `bufOut`
      private[this] var outRemain     = 0
      private[this] var fftSize       = 0  // refreshed as `size + padding`

      private[this] var outSent       = true
      private[this] var isNextFFT     = true

      override def postStop(): Unit = {
        super.postStop()
        fft = null
      }

      @inline
      private[this] def shouldRead    = inRemain == 0 && canRead
      @inline
      private[this] def canPrepareFFT = fftOutRemain == 0 && bufIn0 != null

      @tailrec
      protected def process(): Unit = {
        // becomes `true` if state changes,
        // in that case we run this method again.
        var stateChange = false

        if (shouldRead) {
          readIns()
          inRemain    = bufIn0.size
          inOff       = 0
          stateChange = true
        }

        if (canPrepareFFT) {
          if (isNextFFT) {
            if (bufIn1 != null && inOff < bufIn1.size) {
              size = bufIn1.buf(inOff)
            }
            if (bufIn2 != null && inOff < bufIn2.size) {
              padding = bufIn2.buf(inOff)
            }
            val n = math.max(1, size + padding)
            if (n != fftSize) {
              fftSize = n
              fft     = new DoubleFFT_1D (n)
              fftBuf  = new Array[Double](n)
            }
            fftInOff    = 0
            fftInRemain = size
            isNextFFT   = false
            stateChange = true
          }

          val chunk     = math.min(fftInRemain, inRemain)
          val flushFFT  = inRemain == 0 && fftInOff > 0 && isClosed(shape.in0)
          if (chunk > 0 || flushFFT) {

            Util.copy(bufIn0.buf, inOff, fftBuf, fftInOff, chunk)
            inOff       += chunk
            inRemain    -= chunk
            fftInOff    += chunk
            fftInRemain -= chunk

            if (fftInOff == size || flushFFT) {
              Util.fill(fftBuf, fftInOff, fftSize - fftInOff, 0.0)
              fft.realForward(fftBuf)
              Util.mul(fftBuf, 0, fftSize, 2.0 / fftSize) // scale correctly
              fftOutOff     = 0
              fftOutRemain  = fftSize
              isNextFFT     = true
            }

            stateChange = true
          }
        }

        if (fftOutRemain > 0) {
          if (outSent) {
            bufOut        = ctrl.borrowBufD()
            outRemain     = bufOut.size
            outOff        = 0
            outSent       = false
            stateChange   = true
          }

          val chunk = math.min(fftOutRemain, outRemain)
          if (chunk > 0) {
            Util.copy(fftBuf, fftOutOff, bufOut.buf, outOff, chunk)
            fftOutOff    += chunk
            fftOutRemain -= chunk
            outOff       += chunk
            outRemain    -= chunk
            stateChange   = true
          }
        }

        val flushOut = inRemain == 0 && fftInOff == 0 && fftOutRemain == 0 && isClosed(shape.in0)
        if (!outSent && (outRemain == 0 || flushOut) && isAvailable(shape.out)) {
          if (outOff > 0) {
            bufOut.size = outOff
            push(shape.out, bufOut)
          } else {
            bufOut.release()(ctrl)
          }
          bufOut      = null
          outSent     = true
          stateChange = true
        }

        if (flushOut && outSent) completeStage()
        else if (stateChange) process()
      }
    }
  }
}