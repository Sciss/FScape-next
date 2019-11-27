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

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.{Util, logStream}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

// based on nara_wpe (MIT) : https://github.com/fgnt/nara_wpe
//
// XXX TODO: this is crazily error-prone
// - we need to create abstractions for grouped input traversal
// - we need to introduce complex numbers
// - we need to introduce a linear algebra library
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

    private[this] val inletsSignal: Vec[InD]  = shape.ins0 :+ shape.in1 // last is psd
//    private[this] val inletPSD        : InD   = shape.in1
    private[this] val inletBins       : InI   = shape.in2
    private[this] val inletDelay      : InI   = shape.in3
    private[this] val inletTaps       : InI   = shape.in4
    private[this] val inletAlpha      : InD   = shape.in5
    private[this] val outlets     : Vec[OutD] = shape.outlets

    // XXX TODO --- will be more efficient with flat arrays
    private[this] var invCov: Array[Array[Array[Double]]] = _ // [bins][numChannels * taps][numChannels * taps C]
    private[this] var filter: Array[Array[Array[Double]]] = _ // [numChannels][bins][numChannels * taps C] -- note, different orient than nara_wpe
    private[this] var winBuf: Array[Array[Array[Double]]] = _ // [numChannels][T][bins C] -- note, different orient than nara_wpe
    private[this] var psd   : Array[Double] = _ // real!
    private[this] var pred  : Array[Array[Double]] = _ // prediction [numChannels][bins C]
    private[this] var invCovValid = false
    private[this] var filterValid = false
    private[this] var winValid    = false
    private[this] var psdValid    = false

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

    private[this] val bufsSignalRemain  = new Array[Int](numChannels + 1)  // last is psd
    private[this] val insSignalOff      = new Array[Int](numChannels + 1)  // last is psd
    private[this] val insReadOff        = new Array[Int](numChannels + 1)  // last is psd
    private[this] val bufsOutRemain     = new Array[Int](numChannels)
    private[this] val outsWriteOff      = new Array[Int](numChannels)
    private[this] val outsOff           = new Array[Int](numChannels)

    private[this] val bufsSignal      = new Array[BufD](numChannels + 1)  // last is psd
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
      } else if (isAvailable(inletDelay)) { // XXX TODO --- do we need to check !(bufDelay != null && bufDelayOff < bufDelay.size)
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

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
      winBuf  = null
      invCov  = null
      filter  = null
      psd     = null
      pred    = null
    }

    private final class _InSignalHandlerImpl[T](in: Inlet[T]) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        if (stage == 1) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (stage == 1) process()
      }

      setHandler(in, this)
    }

    private final class _InHandlerImpl[T](in: Inlet[T])(isValid: => Boolean) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        if (stage == 0) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (isValid) {
          if (stage == 0) process()
        } else if (!isAvailable(in)) {
          super.onUpstreamFinish()
        }
      }

      setHandler(in, this)
    }

    private final class OutHandlerImpl[A](out: Outlet[A])
      extends OutHandler {

      override def toString: String = s"OutHandlerImpl($out)"

      def onPull(): Unit = {
        logStream(s"onPull($out)")
        if (stage == 2) process()
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish($out)")
        if (outlets.forall(out => isClosed(out))) {
          super.onDownstreamFinish()
        }
      }

      setHandler(out, this)
    }

    private def installMainAndWindowHandlers(): Unit = {
      inletsSignal.foreach { in =>  // last is inletPSD
        new _InSignalHandlerImpl(in)
      }
//      new _InSignalHandlerImpl(inletPSD)
      new _InHandlerImpl(inletBins  )(bins  >= 0)
      new _InHandlerImpl(inletDelay )(delay >= 0)
      new _InHandlerImpl(inletTaps  )(taps  >= 0)
      new _InHandlerImpl(inletAlpha )(alpha >= 0.0)
      outlets.foreach { out =>
        new OutHandlerImpl(out)
      }
    }

    // constructor
    installMainAndWindowHandlers()

//    private def inValid: Boolean = bins >= 0 && winParamsValid

    private def freeBufInSignal(): Unit = {
      var ch = 0
      while (ch <= numChannels) {  // last is psd
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

      updatePrediction()
      updateKalmanGain()
      updateInvCov()
      updateTaps()
    }

    // eq. (11)
    private def updatePrediction(): Unit = {
      /*
        self.buffer: [taps + delay + 1][bins][numChannels = D]

        window = self.buffer[:-self.delay - 1]  # [taps = K][bins][D]
        # window.transpose(1, 2, 0): [][][]
        window = window.transpose(1, 2, 0).reshape((F, self.taps * D))
        if observation.ndim == 2:
            observation = observation[None, ...]
        prediction = (
            observation[-block_shift] -
            np.einsum('fid,fi->fd', np.conjugate(self.filter_taps), window)


        x{early}(t,f) = y(t,f) - G{herm}(t-1,f) × y(t-𝛥,f)

        filter: [numChannels][bins][numChannels * taps C]
        winBuf: [numChannels][T][bins C]

        for(ch <- 0 until numChannels) pred(ch)(f) = winBuf(ch)(-1)(f) - ...

        np.einsum('fid,fi->fd', x, y) =
          out[f][d] = ∑_i x[f][i][d] * y[f][i]
          = sum(t = 0 until taps; d = 0 until numChannels) { i = d * taps + t; filter(ch)(bin)(i) * window(t)(f) }

        where f = 0 until bins, i = 0 until taps * numChannels, d = 0 until numChannels

       */

      val _taps = taps
      val KD    = _taps + delay
      var ch = 0
      while (ch < numChannels) {
        val x       = pred  (ch)    // [bins C]
        val winBufCh= winBuf(ch)    // [T][bins C]
        val yt      = winBufCh(KD)  // [bins C]
        val window  = winBufCh      // [T][bins C]
        val g       = filter(ch)    // [bins][numChannels, taps C]
        var f_re    = 0
        var f_im    = 1
        var bin     = 0
        val _bins   = bins
        while (bin < _bins) {
          val y_re    = yt(f_re)
          val y_im    = yt(f_im)
          var sum_re  = 0.0
          var sum_im  = 0.0
          val gBin    = g(bin)      // [numChannels, taps C]
          var i_re    = 0
          var i_im    = 1
          var tI      = 0
          while (tI < _taps) {
            val windowT = window(tI)    // [bins C]
            val yD_re   = windowT(f_re)
            val yD_im   = windowT(f_im)
            var chI = 0
            while (chI < numChannels) {
              val g_re =  gBin(i_re)
              val g_im = -gBin(i_im)
              sum_re  += g_re * yD_re - g_im * yD_im
              sum_im  += g_re * yD_im + g_im * yD_re
              chI  += 1
              i_re += 1
              i_im += 1
            }
            tI += 1
          }

          val x_re  = y_re - sum_re
          val x_im  = y_im - sum_im

          x(f_re) = x_re
          x(f_im) = x_im

          bin  += 1
          f_re += 2
          f_im += 2
        }
        ch += 1
      }
    }

    // eq. (14)
    private def updateKalmanGain(): Unit = {
      /*
        nominator = np.einsum('fij,fj->fi', self.inv_cov, window)
        denominator = (self.alpha * self.power).astype(window.dtype)
        denominator += np.einsum('fi,fi->f', np.conjugate(window), nominator)
        self.kalman_gain = nominator / denominator[:, None]

       */
    }

    // eq. (15)
    private def updateInvCov(): Unit = {
      /*
        self.inv_cov = self.inv_cov - np.einsum(
            'fj,fjm,fi->fim',
            np.conjugate(window),
            self.inv_cov,
            self.kalman_gain,
            optimize='optimal'
        )
        self.inv_cov /= self.alpha

       */
    }

    // eq. (16)
    private def updateTaps(): Unit = {
      /*
        self.filter_taps = (
            self.filter_taps +
            np.einsum('fi,fm->fim', self.kalman_gain, np.conjugate(prediction))
        )

       */
    }

//    var STAGE_1_COUNT = 0

    @tailrec
    private def process(): Unit = {
      var stateChange = false

      if (stage == 0) {
        if (needsBins) {
          if (bufBins != null && bufBinsOff < bufBins.size) {
            val newBins = math.max(1, bufBins.buf(bufBinsOff))
            if (bins != newBins) {
              bins        = newBins
              frameSize   = newBins << 1
              invCovValid = false
              filterValid = false
              winValid    = false
              psdValid    = false
            }
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
          stateChange |= tryObtainFrameParams() // always invoke `tryObtainFrameParams`!
        }

        if (!needsBins && !needsFrameParams) {
          minReadOff = 0
          var ch = 0
          while (ch <= numChannels) { // last is psd
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
            // println(s"numChannels = $numChannels, taps = $taps, delay = $delay, frameSize = $frameSize")
            val T     = taps + delay + 1
            winBuf    = Array.ofDim(numChannels, T, frameSize)
            pred      = Array.ofDim(numChannels, frameSize)
            winValid  = true
          }

          if (!psdValid) {
            psd       = new Array(bins)
            psdValid  = true
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
//          STAGE_1_COUNT += 1
          // println(s"stage = 1 ($STAGE_1_COUNT)")
          stateChange = false // "reset" for next stage
//          if (STAGE_1_COUNT == 22) {
//            println("here")
//          }
        }
      }

      if (stage == 1) {
        if (minReadOff < frameSize) {
          var inCloseCount  = 0
          var hasInReadOff  = false
          var checkMin      = false

          var ch = 0
          while (ch <= numChannels) {    // last is psd
            val bufIn = bufsSignal(ch)
            val inRem = bufsSignalRemain(ch)
            if (bufIn != null && inRem > 0) {
              val inReadOff = insReadOff(ch)
              val isPSD = ch == numChannels
              if (isPSD) {
                val chunkH  = math.min((frameSize - inReadOff) >> 1, inRem)
                if (chunkH > 0) {
                  val inOff     = insSignalOff(ch)
                  System.arraycopy(bufIn.buf, inOff, psd, inReadOff >> 1, chunkH)
                  insSignalOff    (ch) = inOff     + chunkH
                  bufsSignalRemain(ch) = inRem     - chunkH
                  insReadOff      (ch) = inReadOff + (chunkH << 1)
                  if (minReadOff == inReadOff) checkMin = true  // minReadOff might have grown
                  stateChange = true
                }
              } else {
                val chunk = math.min(frameSize - inReadOff, inRem)
                if (chunk > 0) {
                  val inOff     = insSignalOff(ch)
                  val winBufCh  = winBuf(ch)
                  val t         = winBufCh.length - 1
                  System.arraycopy(bufIn.buf, inOff, winBufCh(t), inReadOff, chunk)
                  insSignalOff    (ch) = inOff     + chunk
                  bufsSignalRemain(ch) = inRem     - chunk
                  insReadOff      (ch) = inReadOff + chunk
                  if (minReadOff == inReadOff) checkMin = true  // minReadOff might have grown
                  stateChange = true
                }
              }
            } else {
              val inSig = inletsSignal(ch)
              if (isAvailable(inSig)) {
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
                val isPSD = ch == numChannels
                if (!isPSD) inCloseCount += 1
                if (inReadOff > 0) {
                  hasInReadOff = true
                  val chunk = frameSize - inReadOff
                  if (chunk > 0) {
                    if (isPSD) {
                      clearInputTail(psd, readOff = inReadOff >> 1, chunk = chunk >> 1)
                    } else {
                      val winBufCh  = winBuf(ch)
                      val t         = winBufCh.length - 1
                      clearInputTail(winBufCh(t), readOff = inReadOff, chunk = chunk)
                    }
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
            var min = frameSize
            while (ch <= numChannels) {  // last is psd
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

          processFrame()

          // println(s"winInDoneCalcWinOutSize(_, $winInSize) = $writeSize")
          stage       = 2
          // println("stage = 2")
          stateChange = false // "reset" for next stage
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
                val outBuf = bufsOut(ch)
                val outOff = outsOff(ch)
                System.arraycopy(pred(ch), outWriteOff, outBuf.buf, outOff, chunk)
                outsOff       (ch) = outOff      + chunk
                bufsOutRemain (ch) = outRem      - chunk
                outsWriteOff  (ch) = outWriteOff + chunk
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
            // println("stage = 0")
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
