/*
 *  WPE_ReverbFrame.scala
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

package de.sciss.fscape.stream

import akka.stream.Inlet
import akka.stream.stage.{GraphStageLogic, InHandler}
import de.sciss.fscape.{Util, logStream}
import de.sciss.fscape.stream.impl.{NodeImpl, ProcessOutHandlerImpl}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

object WPE_ReverbFrame {
  def apply(in: Vec[OutD], psd: OutD, bins: OutI, delay: OutI, taps: OutI, alpha: OutD): Vec[OutD] = {
    ???
  }

  private final val name = "WPE_ReverbFrame"

  private final case class Shape(ins0: Vec[InD], in1: InD, in2: InI, in3: InI, in4: InI, in5: InD,
                                 outlets: Vec[OutD])
    extends akka.stream.Shape {

    val inlets: Vec[Inlet[_]] = ins0 :+ in1 :+ in2 :+ in3 :+ in4 :+ in5

    def deepCopy(): akka.stream.Shape =
      Shape(ins0 = ins0.map(_.carbonCopy()), in1 = in1.carbonCopy(), in2 = in2.carbonCopy(),
        in3 = in3.carbonCopy(), in4 = in4.carbonCopy(), in5 = in5.carbonCopy(),
        outlets = outlets.map(_.carbonCopy()))
  }

  /* Similar to `DemandWindowedLogic` but for our multi-channel purposes.
   */
  private final class Logic(shape: Shape, layer: Layer, numChannels: Int)(implicit ctrl: Control)
    extends NodeImpl[Shape](name, layer = layer, shape = shape) {

    _: GraphStageLogic =>

    private val inletsSignal: Vec[InD]  = shape.ins0
    private val inletPSD        : InD   = shape.in1
    private val inletBins       : InI   = shape.in2
    private val inletDelay      : InI   = shape.in3
    private val inletTaps       : InI   = shape.in4
    private val inletAlpha      : InD   = shape.in5
    private val outlets     : Vec[OutD] = shape.outlets

    private def winParamsValid: Boolean = {
      ???
    }

    private def freeWinParamBuffers(): Unit = {
      ???
    }

    /* Should return `true` if state was changed. */
    private def tryObtainFrameParams(): Boolean = {
      ???
    }

    private def needsFrameParams: Boolean = {
      ???
    }

    private def requestFrameParams(): Unit = {
      ???
    }

    /* Called when all new window parameters have been obtained.
     * Returns the buffer size for the internal `win` array.
     *
     * The default implementation returns `winInSize`.
     */
    private def allWinParamsReady(winInSize: Int): Int =
      winInSize

    /* Called when the input window has been fully read.
     * The implementation may update the `in` array if needed,
     * or perform additional initializations. It should then
     * return the write-size (`winOutSize`).
     *
     * The default implementation returns `winInSize`.
     */
    private def prepareWindow(win: Array[Array[Double]], winInSize: Int, inSignalDone: Boolean): Unit = {
      ???
    }

    /* The default implementation zeroes the window buffer. */
    private def clearInputTail(win: Array[Double], readOff: Int, chunk: Int): Unit =
      Util.clear(win, readOff, chunk)

    /* The default implementation copies the input to the window. */
    private def processInput(in: Array[Double], inOff: Int, win: Array[Double], readOff: Int, chunk: Int): Unit =
      System.arraycopy(in, inOff, win, readOff, chunk)

    /* Called one or several times per window, when the output buffer
     * should be filled.
     *
     * @param  win         the input window array
     * @param  winInSize   the valid size in `in`
     * @param  writeOff    the number of output frames processed so far.
     *                     This is an accumulation of `chunk` across multiple invocations per window.
     * @param  out         the output window array to fill by this method.
     * @param  winOutSize  the valid size in `out` (as previously reported through `winInDoneCalcWinOutSize`)
     * @param  outOff      the offset in `out` from which on it should be filled
     * @param  chunk       the number of values to fill in `out`
     */
    private def processOutput(win : Array[Double], winInSize : Int, writeOff: Long,
                              out : Array[Double], winOutSize: Long, outOff: Int, chunk: Int): Unit = {
      ???
    }

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
      winBuf = null
    }

    // ---- impl ----

    private[this] var winBuf : Array[Array[Double]] = _

    private[this] var bins      : Int   = -1
    private[this] var frameSize : Int   = -1 // bins times two for complex numbers
    private[this] var maxReadOff: Int   = 0
    private[this] var maxWriteOff: Long  = 0

    private[this] var needsBins  = true

    private[this] val insSignalRemain = new Array[Int](numChannels)
    private[this] val insSignalOff    = new Array[Int](numChannels)
    private[this] val insReadOff      = new Array[Int](numChannels)
    private[this] val outsWriteOff    = new Array[Int](numChannels)
    private[this] val outsRemain      = new Array[Int](numChannels)

    private[this] val bufsInSignal    = new Array[BufD](numChannels)
    private[this] val bufsOut         = new Array[BufD](numChannels)

    private[this] var inBinsOff   : Int   = 0

    private[this] var stage = 0 // 0: gather window parameters, 1: gather input, 2: produce output
    private[this] var inSignalDone = false

    private[this] var bufInBins : BufI = _

    private final class _InSignalHandlerImpl[T](in: Inlet[T], ch: Int) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        process()
      }

      setHandler(in, this)
    }

    private final class _InHandlerImpl[T](in: Inlet[T])(isValid: => Boolean) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (isValid) {
          process()
        } else if (!isAvailable(in)) {
          super.onUpstreamFinish()
        }
      }

      setHandler(in, this)
    }

    private def installMainAndWindowHandlers(): Unit = {
      var ch = 0
      while (ch < numChannels) {
        new _InSignalHandlerImpl(inletsSignal(ch), ch)
        ch += 1
      }
      new _InHandlerImpl(inletBins)(bins >= 0)
      shape.outlets.foreach {
        ??? // new ProcessOutHandlerImpl(_, this)
      }
    }

    // constructor
    installMainAndWindowHandlers()

    private def inValid: Boolean = bins >= 0 && winParamsValid

    private def freeBufInSignal(): Unit = {
      var ch = 0
      while (ch < numChannels) {
        val buf = bufsInSignal(ch)
        if (buf != null) {
          buf.release()
          bufsInSignal(ch) = null
        }
        ch += 1
      }
    }

    private def freeBufInWinSize(): Unit =
      if (bufInBins != null) {
        bufInBins.release()
        bufInBins = null
      }

    private def freeInputBuffers(): Unit = {
      freeBufInSignal()
      freeBufInWinSize()
      freeWinParamBuffers()
    }

    private def freeOutputBuffers(): Unit = {
      var ch = 0
      while (ch < numChannels) {
        val buf = bufsOut(ch)
        if (buf != null) {
          buf.release()
          bufsOut(ch) = null
        }
        ch += 1
      }
    }

    @tailrec
    private def process(): Unit = {
      var stateChange = false

      if (stage == 0) {
        if (needsBins) {
          if (bufInBins != null && inBinsOff < bufInBins.size) {
            bins        = math.max(1, bufInBins.buf(inBinsOff))
            frameSize   = bins << 1
            // println(s"winInSize = $winInSize")
            inBinsOff  += 1
            needsBins   = false
            stateChange = true
          } else if (isAvailable(inletBins)) {
            freeBufInWinSize()
            bufInBins   = grab(inletBins)
            inBinsOff   = 0
            tryPull(inletBins)
            stateChange = true
          } else if (isClosed(inletBins) && bins >= 0) {
            needsBins   = false
            stateChange = true
          }
        }

        if (needsFrameParams) {
          stateChange ||= tryObtainFrameParams()
        }

        if (!needsBins && !needsFrameParams) {
          maxReadOff = 0
          var ch = 0
          while (ch < numChannels) {
            insReadOff(ch) = 0
            ch += 1
          }
          stage       = 1
          val winBufSz = allWinParamsReady(bins)
          // println(s"winBufSz = $winBufSz")
          if (winBuf == null || winBuf.length != winBufSz) {
            winBuf = new Array(winBufSz) // tpeSignal.newArray(winBufSz)
          }
          stateChange = true
        }
      }

      if (stage == 1) {
        if (maxReadOff < frameSize) {
          var ch = 0
          var inCloseCount = 0
          var hasInReadOff = false
          while (ch < numChannels) {
            val bufIn = bufsInSignal(ch)
            val inRem = insSignalRemain(ch)
            if (bufIn != null && inRem > 0) {
              var inReadOff = insReadOff(ch)
              val chunk = math.min(frameSize - inReadOff, inRem)
              if (chunk > 0) {
                val inOff = insSignalOff(ch)
                processInput(in = bufIn.buf, inOff = inOff, win = winBuf(ch), readOff = inReadOff, chunk = chunk)
                insSignalOff    (ch) = inOff + chunk
                insSignalRemain (ch) = inRem - chunk
                inReadOff       += chunk
                insReadOff(ch)   = inReadOff
                maxReadOff       = math.max(maxReadOff, inReadOff)
                stateChange      = true
              }
            } else {
              val inSig = inletsSignal(ch)
              if (isAvailable(inSig)) {
                // freeBufInSignal()
                val oldBuf  = bufsInSignal(ch)
                if (oldBuf != null) {
                  oldBuf.release()
                  // bufsInSignal(ch) = null
                }
                val newBuf = grab(inSig)
                bufsInSignal(ch) = newBuf
                tryPull(inSig)
                //              inSignalAvailable = false
                insSignalOff    (ch)  = 0
                insSignalRemain (ch)  = newBuf.size
                stateChange           = true
              } else if (isClosed(inSig)) {
                // println(s"closed; readOff = $readOff")
                val inReadOff = insReadOff(ch)
                inCloseCount += 1
                if (inReadOff > 0) {
                  hasInReadOff = true
                  val chunk = frameSize - inReadOff
                  if (chunk > 0) {
                    ??? // clearInputTail(winBuf, readOff = readOff, chunk = chunk)
                    insReadOff(ch)  = frameSize
                    stateChange     = true
                  }
                }
                if (inCloseCount == numChannels) {
                  if (!hasInReadOff) {
                    bins      = 0
                    frameSize = 0
                  }
                  inSignalDone  = true
                  stateChange   = true
                }
              }
            }
            ch += 1
          } // while (ch < numChannels)
        }

        if (maxReadOff == frameSize) {
          maxWriteOff = 0
          var ch = 0
          while (ch < numChannels) {
            outsWriteOff(ch) = 0
            ch += 1
          }
          stage       = 2
          /*writeSize   =*/ prepareWindow(winBuf, bins, inSignalDone = inSignalDone)
          // println(s"winInDoneCalcWinOutSize(_, $winInSize) = $writeSize")
          stateChange = true
        }
      }

      if (stage == 2) {
        if (maxWriteOff < frameSize) {
          var ch = 0
          while (ch < numChannels) {
            if (bufsOut(ch) == null) {
              val newBuf      = ctrl.borrowBufD()
              bufsOut   (ch)  = newBuf
              outsRemain(ch)  = newBuf.size
            }

            val outRem = outsRemain(ch)
            if (outRem > 0) {
              var outWriteOff = outsWriteOff(ch)
              val chunk = math.min(frameSize - outWriteOff, outRem) // .toInt
              if (chunk > 0) {
                //            val outBuf = bufsOut(ch)
//                processOutput(
//                  win = winBuf(ch)      , winInSize = bins , writeOff  = writeOff,
//                  out = outBuf.buf , winOutSize  = writeSize , outOff    = outOff,
//                  chunk = chunk
//                )
                ???
                outWriteOff      += chunk
                outsWriteOff(ch)  = outWriteOff
                maxWriteOff       = math.max(maxWriteOff, outWriteOff)
                outsRemain  (ch)  = outRem - chunk
                stateChange       = true
              }
            }

            if (outsRemain(ch) == 0 && isAvailable(outlets(ch))) {
              push(outlets(ch), bufsOut(ch))
              bufsOut(ch) = null
              stateChange = true
            }

            ch += 1
          } // while (ch < numChannels)
        }

        if (maxWriteOff == frameSize) {
          if (inSignalDone) {
            var ch = 0
            var outPushCount = 0
            while (ch < numChannels) {
              val outlet  = outlets(ch)
              val out     = bufsOut(ch)
              if (out == null) {
                outPushCount += 1
              } else if (isAvailable(outlet)) {
                out.size -= outsRemain(ch)
                // don't bother to update `outsRemain` because it will be done later when buf is null

                if (out.size > 0) {
                  push(outlet, out)
                } else {
                  out.release()
                }
                bufsOut(ch) = null
                outPushCount += 1
              }
              ch += 1
            }
            if (outPushCount == numChannels) {
              completeStage()
            }
          }
          else {
            stage       = 0
            needsBins   = true
            requestFrameParams() // needsWinParams= true
            stateChange = true
          }
        }
      }

      if (stateChange) process()
    }
  }
}
