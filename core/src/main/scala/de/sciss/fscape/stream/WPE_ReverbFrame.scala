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

import akka.stream.stage.{GraphStageLogic, InHandler}
import akka.stream.{Attributes, Inlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.{Util, logStream}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

object WPE_ReverbFrame {
  def apply(in: Vec[OutD], psd: OutD, bins: OutI, delay: OutI, taps: OutI, alpha: OutD)
           (implicit b: Builder): Vec[OutD] = {
    val numChannels = in.size
    val source      = new Stage(layer = b.layer, numChannels = numChannels)
    val stage       = b.add(source)
    (in zip stage.ins0).foreach { case (output, input) =>
      b.connect(output, input)
    }
    b.connect(psd   , stage.in1)
    b.connect(bins  , stage.in2)
    b.connect(delay , stage.in3)
    b.connect(taps  , stage.in4)
    b.connect(alpha , stage.in5)
    stage.outlets // .toIndexedSeq
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

  private final class Stage(layer: Layer, numChannels: Int)(implicit ctrl: Control)
    extends StageImpl[Shape](name) {

    val shape = Shape(
      ins0    = Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch")),
      in1     = InD (s"$name.psd"),
      in2     = InI (s"$name.bins"),
      in3     = InI (s"$name.delay"),
      in4     = InI (s"$name.taps"),
      in5     = InD (s"$name.alpha"),
      outlets = Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, numChannels = numChannels)
  }

  /* Similar to `DemandWindowedLogic` but for our multi-channel purposes.
   */
  private final class Logic(shape: Shape, layer: Layer, numChannels: Int)(implicit ctrl: Control)
    extends NodeImpl[Shape](name, layer = layer, shape = shape) {

    _: GraphStageLogic =>

    private[this] val inletsSignal: Vec[InD]  = shape.ins0
    private[this] val inletPSD        : InD   = shape.in1
    private[this] val inletBins       : InI   = shape.in2
    private[this] val inletDelay      : InI   = shape.in3
    private[this] val inletTaps       : InI   = shape.in4
    private[this] val inletAlpha      : InD   = shape.in5
    private[this] val outlets     : Vec[OutD] = shape.outlets

    // XXX TODO --- will be more efficient with flat arrays
    private[this] var invCov: Array[Array[Array[Double]]] = _ // [bins][numChannels * taps][numChannels * taps C]
    private[this] var filter: Array[Array[Array[Double]]] = _ // [numChannels][bins][numChannels * taps C] -- note, different orient than nara_wpe
    private[this] var winBuf: Array[Array[Array[Double]]] = _ // [numChannels][T][bins C] -- note, different orient than nara_wpe
    private[this] var invCovValid = false
    private[this] var filterValid = false
    private[this] var winValid    = false

    private[this] var bins      : Int     = -1
    private[this] var frameSize : Int     = -1 // bins times two for complex numbers
    private[this] var delay     : Int     = -1
    private[this] var taps      : Int     = -1
    private[this] var alpha     : Double  = -1.0

    private def delayValid  = delay  >= 0
    private def tapsValid   = taps   >= 0
    private def alphaValid  = alpha  >= 0

    private[this] var minReadOff  : Int   = 0
    private[this] var minWriteOff : Int   = 0

    private[this] var needsBins  = true
    private[this] var needsDelay = true
    private[this] var needsTaps  = true
    private[this] var needsAlpha = true

    private[this] var bufBinsOff  : Int   = 0
    private[this] var bufBins     : BufI = _
    private[this] var bufDelayOff : Int   = 0
    private[this] var bufDelay    : BufI  = _
    private[this] var bufTapsOff  : Int   = 0
    private[this] var bufTaps     : BufI  = _
    private[this] var bufAlphaOff : Int   = 0
    private[this] var bufAlpha    : BufD  = _

    private[this] val bufsSignalRemain  = new Array[Int](numChannels)
    private[this] val insSignalOff      = new Array[Int](numChannels)
    private[this] val insReadOff        = new Array[Int](numChannels)
    private[this] val bufsOutRemain     = new Array[Int](numChannels)
    private[this] val outsWriteOff      = new Array[Int](numChannels)
    private[this] val outsOff           = new Array[Int](numChannels)

    private[this] var bufPsdOff   : Int   = 0
    private[this] var bufPsdRemain: Int   = 0
    private[this] var bufPsd      : BufD  = _
    private[this] var psdReadOff      = 0

    private[this] val bufsSignal      = new Array[BufD](numChannels)
    private[this] val bufsOut         = new Array[BufD](numChannels)

    private[this] var stage = 0 // 0: gather window parameters, 1: gather input, 2: produce output
    private[this] var signalDone = false

//    private def winParamsValid: Boolean = ...

    private def freeFrameParamBuffers(): Unit = {
      freeDelayBuf()
      freeTapsBuf ()
      freeAlphaBuf()
    }

    private def freeDelayBuf(): Unit =
      if (bufDelay != null) {
        bufDelay.release()
        bufDelay = null
      }

    private def freeTapsBuf(): Unit =
      if (bufTaps != null) {
        bufTaps.release()
        bufTaps = null
      }

    private def freeAlphaBuf(): Unit =
      if (bufAlpha != null) {
        bufAlpha.release()
        bufAlpha = null
      }

    /* Should return `true` if state was changed. */
    private def tryObtainFrameParams(): Boolean = {
      var stateChange = false

      if (needsDelay && bufDelay != null && bufDelayOff < bufDelay.size) {
        val newDelay = math.max(0, bufDelay.buf(bufDelayOff))
        if (delay != newDelay) {
          delay     = newDelay
          winValid  = false
        }
        bufDelayOff += 1
        needsDelay  = false
        stateChange = true
      } else if (isAvailable(inletDelay)) {
        freeDelayBuf()
        bufDelay    = grab(inletDelay)
        bufDelayOff = 0
        tryPull(inletDelay)
        stateChange = true
      } else if (needsDelay && isClosed(inletDelay) && delayValid) {
        needsDelay  = false
        stateChange = true
      }

      // XXX TODO --- gosh, we need to put this into a state-class
      if (needsTaps && bufTaps != null && bufTapsOff < bufTaps.size) {
        val newTaps = math.max(1, bufTaps.buf(bufTapsOff))
        if (taps != newTaps) {
          taps        = newTaps
          winValid    = false
          invCovValid = false
          filterValid = false
        }
        bufTapsOff += 1
        needsTaps   = false
        stateChange = true
      } else if (isAvailable(inletTaps)) {
        freeTapsBuf()
        bufTaps     = grab(inletTaps)
        bufTapsOff  = 0
        tryPull(inletTaps)
        stateChange = true
      } else if (needsTaps && isClosed(inletTaps) && tapsValid) {
        needsTaps   = false
        stateChange = true
      }

      if (needsAlpha && bufAlpha != null && bufAlphaOff < bufAlpha.size) {
        alpha        = math.max(1.0e-10, bufAlpha.buf(bufAlphaOff))
        bufAlphaOff += 1
        needsAlpha   = false
        stateChange = true
      } else if (isAvailable(inletAlpha)) {
        freeAlphaBuf()
        bufAlpha     = grab(inletAlpha)
        bufAlphaOff  = 0
        tryPull(inletAlpha)
        stateChange = true
      } else if (needsAlpha && isClosed(inletAlpha) && alphaValid) {
        needsAlpha   = false
        stateChange = true
      }

      stateChange
    }

    private def needsFrameParams: Boolean = 
      needsDelay || needsTaps || needsAlpha

    private def requestFrameParams(): Unit = {
      needsDelay = true
      needsTaps  = true
      needsAlpha = true
    }

    /* The default implementation zeroes the window buffer. */
    private def clearInputTail(win: Array[Double], readOff: Int, chunk: Int): Unit =
      Util.clear(win, readOff, chunk)

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
    private def processOutput(win : Array[Double], winInSize : Int, writeOff: Int,
                              out : Array[Double], winOutSize: Int, outOff: Int, chunk: Int): Unit = {
      System.arraycopy(win, writeOff, out, outOff, chunk)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
      winBuf  = null
      invCov  = null
      filter  = null
    }

    private final class _InSignalHandlerImpl[T](in: Inlet[T]) extends InHandler {
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
      inletsSignal.foreach { in =>
        new _InSignalHandlerImpl(in)
      }
      new _InSignalHandlerImpl(inletPSD)
      new _InHandlerImpl(inletBins)(bins >= 0)
      outlets.foreach {
        ??? // new ProcessOutHandlerImpl(_, this)
      }
    }

    // constructor
    installMainAndWindowHandlers()

//    private def inValid: Boolean = bins >= 0 && winParamsValid

    private def freeBufInSignal(): Unit = {
      var ch = 0
      while (ch < numChannels) {
        val buf = bufsSignal(ch)
        if (buf != null) {
          buf.release()
          bufsSignal(ch) = null
        }
        ch += 1
      }
    }

    private def freeBufInWinSize(): Unit =
      if (bufBins != null) {
        bufBins.release()
        bufBins = null
      }

    private def freeInputBuffers(): Unit = {
      freeBufInSignal()
      freeBufInWinSize()
      freeFrameParamBuffers()
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

    private def processFrame(): Unit = {
      /*

        # frame: (F, D) --- in our case: [D][F]
        prediction, window = self._get_prediction(frame)

        self._update_kalman_gain(window)
        self._update_inv_cov(window)
        self._update_taps(prediction)

       */
      ???
    }

    // eq. (11)
    private def updatePrediction(): Unit = {

    }

    // eq. (14)
    private def updateKalmanGain(): Unit = {

    }

    // eq. (15)
    private def updateInvCov(): Unit = {

    }

    // eq. (16)
    private def updateTaps(): Unit = {

    }

    @tailrec
    private def process(): Unit = {
      var stateChange = false

      if (stage == 0) {
        if (needsBins) {
          if (bufBins != null && bufBinsOff < bufBins.size) {
            bins        = math.max(1, bufBins.buf(bufBinsOff))
            frameSize   = bins << 1
            // println(s"winInSize = $winInSize")
            bufBinsOff  += 1
            needsBins   = false
            stateChange = true
          } else if (isAvailable(inletBins)) {
            freeBufInWinSize()
            bufBins   = grab(inletBins)
            bufBinsOff   = 0
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
          minReadOff = 0
          var ch = 0
          while (ch < numChannels) {
            insReadOff(ch) = 0
            ch += 1
          }

          if (winValid) {
            // rotate
            var ch = 0
            val winBufCh0 = winBuf(0)
            val numChM = numChannels - 1
            while (ch < numChM) {
              winBuf(ch) = winBuf(ch + 1)
              ch += 1
            }
            winBuf(numChM) = winBufCh0

          } else {
            val T     = taps + delay + 1
            winBuf    = Array.ofDim(numChannels, T, frameSize)
            winValid  = true
          }

          if (!invCovValid) {
            val I       = numChannels * taps
            val IC      = I << 1
            invCov      = Array.tabulate(bins, I, IC) { case (_, i, j) =>
              if ((i << 1) == j) 1.0 else 0.0   // "eye"
            }
            invCovValid = true
          }

          if (!filterValid) {
            val I       = numChannels * taps
            val IC      = I << 1
            filter      = Array.ofDim(numChannels, bins, IC)
            filterValid = true
          }

          stage       = 1
          stateChange = true
        }
      }

      if (stage == 1) {
        if (minReadOff < frameSize) {
          var inCloseCount  = 0
          var hasInReadOff  = false
          var checkMin      = false

          ??? // psd

          var ch = 0
          while (ch < numChannels) {
            val bufIn = bufsSignal(ch)
            val inRem = bufsSignalRemain(ch)
            if (bufIn != null && inRem > 0) {
              val inReadOff = insReadOff(ch)
              val chunk = math.min(frameSize - inReadOff, inRem)
              if (chunk > 0) {
                val inOff     = insSignalOff(ch)
                val winBufCh  = winBuf(ch)
                val t         = winBufCh.length - 1
                System.arraycopy(bufIn.buf, inOff, winBufCh(t), inReadOff, chunk)
                insSignalOff    (ch) = inOff     + chunk
                bufsSignalRemain (ch) = inRem     - chunk
                insReadOff      (ch) = inReadOff + chunk
                if (minReadOff == inReadOff) checkMin = true  // minReadOff might have grown
                stateChange = true
              }
            } else {
              val inSig = inletsSignal(ch)
              if (isAvailable(inSig)) {
                // freeBufInSignal()
                val oldBuf  = bufsSignal(ch)
                if (oldBuf != null) {
                  oldBuf.release()
                  // bufsInSignal(ch) = null
                }
                val newBuf = grab(inSig)
                bufsSignal(ch) = newBuf
                tryPull(inSig)
                // inSignalAvailable = false
                insSignalOff    (ch)  = 0
                bufsSignalRemain(ch)  = newBuf.size
                stateChange           = true
              } else if (isClosed(inSig)) {
                // println(s"closed; readOff = $readOff")
                val inReadOff = insReadOff(ch)
                inCloseCount += 1
                if (inReadOff > 0) {
                  hasInReadOff = true
                  val chunk = frameSize - inReadOff
                  if (chunk > 0) {
                    val winBufCh  = winBuf(ch)
                    val t         = winBufCh.length - 1
                    clearInputTail(winBufCh(t), readOff = inReadOff, chunk = chunk)
                    insReadOff(ch)  = frameSize
                    stateChange     = true
                  }
                }
              }
            }
            ch += 1
          } // while (ch < numChannels)

          if (checkMin) {
            var ch = 0
            var min = psdReadOff
            while (ch < numChannels) {
              min = math.min(min, insReadOff(ch))
              ch += 1
            }
            minReadOff = min
          }

          if (inCloseCount == numChannels) {
            if (!hasInReadOff) {
              bins      = 0
              frameSize = 0
            }
            signalDone  = true
            stateChange = true
          }
        }

        if (minReadOff == frameSize) {
          minWriteOff = 0
          var ch = 0
          while (ch < numChannels) {
            outsWriteOff(ch) = 0
            ch += 1
          }
          stage       = 2

          processFrame()

          // println(s"winInDoneCalcWinOutSize(_, $winInSize) = $writeSize")
          stateChange = true
        }
      }

      if (stage == 2) {
        if (minWriteOff < frameSize) {
          var checkMin = false
          var ch = 0
          while (ch < numChannels) {
            if (bufsOut(ch) == null) {
              val newBuf      = ctrl.borrowBufD()
              bufsOut   (ch)  = newBuf
              outsOff   (ch)  = 0
              bufsOutRemain(ch)  = newBuf.size
            }

            val outRem = bufsOutRemain(ch)
            if (outRem > 0) {
              val outWriteOff = outsWriteOff(ch)
              val chunk = math.min(frameSize - outWriteOff, outRem) // .toInt
              if (chunk > 0) {
                //            val outBuf = bufsOut(ch)
                val outOff = outsOff     (ch)
//                processOutput(
//                  win = winBuf(ch)      , winInSize = bins , writeOff  = writeOff,
//                  out = outBuf.buf , winOutSize  = writeSize , outOff    = outOff,
//                  chunk = chunk
//                )
                outsOff     (ch) = outOff      + chunk
                bufsOutRemain  (ch) = outRem      - chunk
                outsWriteOff(ch) = outWriteOff + chunk
                if (minWriteOff == outWriteOff) checkMin = true  // minWriteOff might have grown
                stateChange = true
              }
            }

            if (checkMin) {
              var ch = 0
              var min = frameSize
              while (ch < numChannels) {
                min = math.min(min, outsWriteOff(ch))
                ch += 1
              }
              minWriteOff = min
            }

            if (bufsOutRemain(ch) == 0 && isAvailable(outlets(ch))) {
              push(outlets(ch), bufsOut(ch))
              bufsOut(ch) = null
              stateChange = true
            }

            ch += 1
          } // while (ch < numChannels)
        }

        if (minWriteOff == frameSize) {
          if (signalDone) {
            var ch = 0
            var outPushCount = 0
            while (ch < numChannels) {
              val outlet  = outlets(ch)
              val out     = bufsOut(ch)
              if (out == null) {
                outPushCount += 1
              } else if (isAvailable(outlet)) {
                out.size -= bufsOutRemain(ch)
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
