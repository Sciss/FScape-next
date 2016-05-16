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

import java.{util => ju}

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape3, Graph, Inlet, Outlet}
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

import scala.annotation.tailrec

object Real1FFT {
  def apply(in: Outlet /* Signal */[Double], size: Int /* Signal[Int] */, padding: Int /* Signal[Int] */ = 0)
           (implicit b: GraphDSL.Builder[NotUsed]): Outlet /* Signal */[Double] = {

    val fftSize = size + padding

    import GraphDSL.Implicits._

    val res = in.grouped(size).statefulMapConcat[Double] { () =>
      val fft = new DoubleFFT_1D(fftSize)
      val arr = new Array[Double](fftSize)

      seq =>
        var i = 0
        seq.foreach { d =>
          arr(i) = d
          i += 1
        }
        while (i < fftSize) {
          arr(i) = 0.0
          i += 1
        }
        fft.realForward(arr)
        arr.toVector
    }

    res.outlet
  }

  final class BufD(val buf: Array[Double], @volatile var size: Int)
  final class BufI(val buf: Array[Int]   , @volatile var size: Int)

  trait Control {
    def borrowBufD(): BufD
    def borrowBufI(): BufI

    def returnBufD(buf: BufD): Unit
    def returnBufI(buf: BufI): Unit
  }

  private final class Impl(ctrl: Control) extends GraphStage[FanInShape3[BufD, BufI, BufI, BufD]] {

    private[this] val inIn      = Inlet [BufD]("Real1FFT.in"     )
    private[this] val inSize    = Inlet [BufI]("Real1FFT.size"   )
    private[this] val inPadding = Inlet [BufI]("Real1FFT.padding")
    private[this] val out       = Outlet[BufD]("Real1FFT.out"    )

    val shape = new FanInShape3(in0 = inIn, in1 = inSize, in2 = inPadding, out = out)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private[this] var fft: DoubleFFT_1D = _
      private[this] var pending: Int = _

      private[this] var bufIn     : BufD = _
      private[this] var bufSize   : BufI = _
      private[this] var bufPadding: BufI = _
      private[this] var bufOut    : BufD = _

      private[this] var size      : Int  = 0
      private[this] var padding   : Int  = _

      private[this] var fftBuf    : Array[Double] = _
      private[this] var fftInOff  : Int  = 0  // regarding `fftBuf`
      private[this] var fftOutOff : Int  = 0  // regarding `fftBuf`
      private[this] var inOff     : Int  = 0  // regarding `bufIn`
      private[this] var outOff    : Int  = 0  // regarding `bufOut`
      private[this] var fftSize   : Int  = 0  // refreshed as `size + padding`

      override def preStart(): Unit = {
        pull(inIn)
        pull(inSize)
        pull(inPadding)
        pending = 3
      }

      private def decPending(): Unit = {
        pending -= 1
        if (pending == 0) process()
      }

      /*

        if (fft-read full) reset-fft-buf

       */

      private[this] var inRemain      = 0
      private[this] var fftInRemain   = 0
      private[this] var fftOutRemain  = 0
      private[this] var outRemain     = 0
      private[this] var outSent       = true
      private[this] var inRead        = true

      private[this] var canRead       = false

      @tailrec
      private def process(): Unit = {
        // becomes `true` if state changes,
        // in that case we run this method again.
        var iterate = false

        if (!inRead && inRemain == 0) {
          if (bufIn != null) ctrl.returnBufD(bufIn)
          bufIn     = grab(inIn)
          inRemain  = bufIn.size
          inOff     = 0
          pull(inIn)

          if (bufSize != null) ctrl.returnBufI(bufSize)
          if (isClosed(inSize)) {
            bufSize = null
          } else {
            bufSize = grab(inSize)
          }

          if (bufPadding != null) ctrl.returnBufI(bufPadding)
          if (isClosed(inPadding)) {
            bufPadding = null
          } else {
            bufPadding = grab(inPadding)
          }

          inRead    = true
          iterate   = true
        }

        if (fftOutRemain == 0) {
          val chunk = math.min(size - fftInOff, inRemain)
          val flush = inRemain == 0 && isClosed(inIn) && fftInOff > 0
          if (chunk > 0 || flush) {

            if (fftInOff == 0 /* size */) { // begin new block
              size    = ???
              padding = ???
              val n = math.max(1, size + padding)
              if (n != fftSize) {
                fftSize = n
                fft     = new DoubleFFT_1D (n)
                fftBuf  = new Array[Double](n)
              }
              fftInOff = 0
            }

            System.arraycopy(bufIn.buf, inOff, fftBuf, fftInOff, chunk)
            inOff    += chunk
            inRemain -= chunk
            fftInOff += chunk

            if (fftInOff == size || flush) {
              ju.Arrays.fill(fftBuf, fftInOff, fftSize, 0.0)
              fft.realForward(fftBuf)
              fftInOff      = 0
              fftOutRemain  = fftSize
              fftOutOff     = 0
              iterate       = true
            }
          }
        }

        if (fftOutRemain > 0) {
          if (outSent) {
            bufOut    = ctrl.borrowBufD()
            outRemain = bufOut.size
            outOff    = 0
            outSent   = false
          }

          val chunk = math.min(fftOutRemain, outRemain)
          if (chunk > 0) {
            System.arraycopy(fftBuf, fftOutOff, bufOut.buf, outOff, chunk)
            fftOutOff    += chunk
            fftOutRemain -= chunk
            outOff       += chunk
            outRemain    -= chunk
            iterate       = true
          }
        }

        val flush = ???
        if (!outSent && (outRemain == 0 || flush) && isAvailable(out)) {
          bufOut.size = outOff
          push(out, bufOut) // XXX
          iterate   = true
          outSent   = true
        }

        if (iterate) process()
      }

      setHandler(inIn, new InHandler {
        def onPush(): Unit = {
          bufIn = grab(inIn)
          decPending()
        }
      })

      setHandler(inSize, new InHandler {
        def onPush(): Unit = {
          bufSize = grab(inSize)
          decPending()
        }

        override def onUpstreamFinish(): Unit = ()  // keep running
      })

      setHandler(inPadding, new InHandler {
        def onPush(): Unit = {
          bufPadding = grab(inPadding)
          decPending()
        }

        override def onUpstreamFinish(): Unit = ()  // keep running
      })

      setHandler(out, new OutHandler {
        def onPull(): Unit = ???

        override def onDownstreamFinish(): Unit = super.onDownstreamFinish()
      })
    }
  }
}