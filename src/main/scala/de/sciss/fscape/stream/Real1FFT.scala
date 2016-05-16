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
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.Util
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

    private[this] val inIn      = Inlet [BufD]("Real1FFT.in"     )
    private[this] val inSize    = Inlet [BufI]("Real1FFT.size"   )
    private[this] val inPadding = Inlet [BufI]("Real1FFT.padding")
    private[this] val out       = Outlet[BufD]("Real1FFT.out"    )

    val shape = new FanInShape3(in0 = inIn, in1 = inSize, in2 = inPadding, out = out)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      private[this] var fft       : DoubleFFT_1D  = _
      private[this] var fftBuf    : Array[Double] = _

      private[this] var bufIn     : BufD = _
      private[this] var bufSize   : BufI = _
      private[this] var bufPadding: BufI = _
      private[this] var bufOut    : BufD = _

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
      private[this] var canRead       = false

      override def preStart(): Unit = {
        pull(inIn)
        pull(inSize)
        pull(inPadding)
      }

      @inline
      private[this] def shouldRead    = inRemain == 0 && canRead
      @inline
      private[this] def canPrepareFFT = fftOutRemain == 0 && bufIn != null

      private def updateCanRead(): Unit = {
        canRead = isAvailable(inIn) &&
          (isClosed(inSize)    || isAvailable(inSize)) &&
          (isClosed(inPadding) || isAvailable(inPadding))
        if (shouldRead) process()
      }

      private def freeInputBuffers(): Unit = {
        if (bufIn != null) {
          ctrl.returnBufD(bufIn)
          bufIn = null
        }
        if (bufSize != null) {
          ctrl.returnBufI(bufSize)
          bufSize = null
        }
        if (bufPadding != null) {
          ctrl.returnBufI(bufPadding)
          bufPadding = null
        }
      }

      private def freeOutputBuffers(): Unit =
        if (bufOut != null) {
          ctrl.returnBufD(bufOut)
          bufOut = null
        }

      @tailrec
      private def process(): Unit = {
        // becomes `true` if state changes,
        // in that case we run this method again.
        var stateChange = false

        if (shouldRead) {
          freeInputBuffers()
          bufIn     = grab(inIn)
          inRemain  = bufIn.size
          inOff     = 0
          tryPull(inIn)

          if (isAvailable(inSize)) {
            bufSize = grab(inSize)
            tryPull(inSize)
          }

          if (isAvailable(inPadding)) {
            bufPadding = grab(inPadding)
            tryPull(inPadding)
          }

          canRead     = false
          stateChange = true
        }

        if (canPrepareFFT) {
          if (isNextFFT) {
            if (bufSize != null && inOff < bufSize.size) {
              size = bufSize.buf(inOff)
            }
            if (bufPadding != null && inOff < bufPadding.size) {
              padding = bufPadding.buf(inOff)
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
          val flushFFT  = inRemain == 0 && fftInOff > 0 && isClosed(inIn)
          if (chunk > 0 || flushFFT) {

            Util.copy(bufIn.buf, inOff, fftBuf, fftInOff, chunk)
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

        val flushOut = inRemain == 0 && fftInOff == 0 && fftOutRemain == 0 && isClosed(inIn)
        if (!outSent && (outRemain == 0 || flushOut) && isAvailable(out)) {
          if (outOff > 0) {
            bufOut.size = outOff
            push(out, bufOut)
          } else {
            ctrl.returnBufD(bufOut)
          }
          bufOut      = null
          outSent     = true
          stateChange = true
        }

        if (flushOut && outSent) completeStage()
        else if (stateChange) process()
      }

      setHandler(inIn, new InHandler {
        def onPush(): Unit = updateCanRead()

        override def onUpstreamFinish(): Unit = process() // may lead to `flushOut`
      })

      setHandler(inSize, new InHandler {
        def onPush(): Unit = updateCanRead()

        override def onUpstreamFinish(): Unit = ()  // keep running
      })

      setHandler(inPadding, new InHandler {
        def onPush(): Unit = updateCanRead()

        override def onUpstreamFinish(): Unit = ()  // keep running
      })

      setHandler(out, new OutHandler {
        def onPull(): Unit = process()
      })

      override def postStop(): Unit = {
        freeInputBuffers()
        freeOutputBuffers()
        fft = null
      }
    }
  }
}