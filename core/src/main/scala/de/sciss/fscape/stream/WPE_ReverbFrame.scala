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

    // XXX TODO --- perhaps more efficient with flat arrays
    private[this] var winBuf: Array[Array[Array[Double]]] = _ // [numChannels][T][bins C] -- NOTE, different orient than nara_wpe
    private[this] var invCov: Array[Array[Array[Double]]] = _ // [bins + 1][numChannels * taps][numChannels * taps C] -- last is aux
    private[this] var filter: Array[Array[Array[Double]]] = _ // [numChannels][bins][numChannels * taps C] -- NOTE, different orient than nara_wpe
    private[this] var kalman: Array[Array[Double]]        = _ // [bins][numChannels * taps C]
    private[this] var psd   : Array[Double]               = _ // [bins] real!
    private[this] var pred  : Array[Array[Double]]        = _ // [numChannels][bins C]
    private[this] var buffersValid = false

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
          delay         = newDelay
          buffersValid  = false
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
          taps          = newTaps
          buffersValid  = false
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
      winBuf    = null
      invCov    = null
      filter    = null
      kalman    = null
      psd       = null
      pred      = null
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
      updateFilter()
    }

    // eq. (11)
    private def updatePrediction(): Unit = {
      /*
        buffer    : [taps + delay + 1][bins][numChannels = D] complex
        prediction: [F][D]                                    complex

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
        val winCh   = winBuf(ch)    // [T][bins C]
        val yt      = winCh(KD)     // [bins C]
        val fltCh   = filter(ch)    // [bins][numChannels, taps C]
        var f_re    = 0
        var f_im    = 1
        var bin     = 0
        val _bins   = bins
        while (bin < _bins) {
          val y_re    = yt(f_re)
          val y_im    = yt(f_im)
          var sum_re  = 0.0
          var sum_im  = 0.0
          val fltChF  = fltCh(bin)      // [numChannels, taps C]
          var i_re    = 0
          var i_im    = 1
          var tI      = 0
          while (tI < _taps) {
            val winChT  = winCh(tI)    // [bins C]
            val yD_re   = winChT(f_re)
            val yD_im   = winChT(f_im)
            var chI = 0
            while (chI < numChannels) {
              val g_re =  fltChF(i_re)
              val g_im = -fltChF(i_im)
              sum_re  += g_re * yD_re - g_im * yD_im
              sum_im  += g_re * yD_im + g_im * yD_re
              chI  += 1
              i_re += 2
              i_im += 2
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
        inv_cov     : [F][D * K][D * K] complex
        window      : [F][D * K] complex
        nominator   : [F][D * K] complex
        power       : [F] real
        denominator : [F] complex
        kalman_gain : [F][D * K] complex

        winBuf: [numChannels][T][bins C]

        nominator = np.einsum('fij,fj->fi', self.inv_cov, window)
        denominator = (self.alpha * self.power).astype(window.dtype)
        denominator += np.einsum('fi,fi->f', np.conjugate(window), nominator)
        self.kalman_gain = nominator / denominator[:, None]

        nominator: for(f <- 0 until bins} {
          for (i <- 0 until DK) {
            n = out[f][i] = sum(t = 0 until taps; d = 0 until numChannels) {
              j = d * taps + t
              inv_cov[f][i][j] * window[d][t][f]
            }
          }
        }

        denominatorPlus: for(f <- 0 until bins) {
          sum (t = 0 until taps; d = 0 until numChannels) {
            i = d * taps + t
            conj(window[d][t][f]) * nominator[f][i]
          }
        }

        We calculate nominator first (in kalman), then the denominator and divide in-place in kalman

       */

      val _bins   = bins
      val _alpha  = alpha
      val _taps   = taps
      val DK      = numChannels * _taps
      val _invCov = invCov  // [bins][numChannels * taps][numChannels * taps C]
      val _kalman = kalman  // [bins][numChannels * taps C]
      val _psd    = psd     // [bins] real!
      val _winBuf = winBuf  // [numChannels][T][bins C]
      var f_re    = 0
      var f_im    = 1
      var bin     = 0
      while (bin < _bins) {
        // we begin with the nominator matrix
        val invCovF = _invCov(bin)  // [numChannels * taps][numChannels * taps C]
        val kalmanF = _kalman(bin)  // [numChannels * taps C]
        var i       = 0
        var i_re    = 0
        var i_im    = 1
        while (i < DK) {
          val invCovFI = invCovF(i) // [numChannels * taps C]
          var tJ      = 0
          var j_re    = 0
          var j_im    = 1
          var nom_re  = 0.0
          var nom_im  = 0.0
          while (tJ < _taps) {
            var chJ = 0
            while (chJ < numChannels) {
              val inv_re  = invCovFI(j_re)
              val inv_im  = invCovFI(j_im)
              val winChT  = _winBuf(chJ)(tJ)
              val win_re  = winChT(f_re)
              val win_im  = winChT(f_im)
              nom_re  += inv_re * win_re - inv_im * win_im
              nom_im  += inv_re * win_im + inv_im * win_re
              chJ  += 1
              j_re += 2
              j_im += 2
            }
            tJ += 1
          }
          // here kalman becomes nominator
          kalmanF(i_re) = nom_re
          kalmanF(i_im) = nom_im
          i    += 1
          i_re += 2
          i_im += 2
        }
        // now we construct the denominator vector
        val denomA = _alpha * _psd(bin) // real
        var denom_re  = denomA
        var denom_im  = 0.0
        var tI      = 0
        /*var*/ i_re    = 0
        /*var*/ i_im    = 1
        while (tI < _taps) {
          tI += 1
          var chI = 0
          while (chI < numChannels) {
            val winChT  = _winBuf(chI)(tI)
            val win_re  =  winChT(f_re)
            val win_im  = -winChT(f_im)
            val nom_re  = kalmanF(i_re)
            val nom_im  = kalmanF(i_im)
            denom_re += win_re * nom_re - win_im * nom_im
            denom_im += win_re * nom_im + win_im * nom_re
            chI  += 1
            i_re += 2
            i_im += 2
          }
        }
        // now we divide by the denominator
        val denomAbsSq  = denom_re * denom_re + denom_im * denom_im
        val denomR_re   = if (denomAbsSq == 0.0) 0.0 else  denom_re / denomAbsSq
        val denomR_im   = if (denomAbsSq == 0.0) 0.0 else -denom_im / denomAbsSq

        /*var*/ i       = 0
        /*var*/ i_re    = 0
        /*var*/ i_im    = 1
        while (i < DK) {
          val nom_re    = kalmanF(i_re)
          val nom_im    = kalmanF(i_im)
          kalmanF(i_re) = nom_re * denomR_re - nom_im * denomR_im
          kalmanF(i_im) = nom_re * denomR_im + nom_im * denomR_re
          i    += 1
          i_re += 2
          i_im += 2
        }

        bin  += 1
        f_re += 2
        f_im += 2
      }
    }

    // eq. (15)
    private def updateInvCov(): Unit = {
      /*
        window      : [F][D * K]        complex
        inv_cov     : [F][D * K][D * K] complex
        kalman_gain : [F][D * K]        complex

        self.inv_cov = self.inv_cov - np.einsum(
            'fj,fjm,fi->fim',
            np.conjugate(window),
            self.inv_cov,
            self.kalman_gain,
            optimize='optimal'
        )
        self.inv_cov /= self.alpha

        np.einsum('fj,fjm,fi->fim', x, y, z)
        np.einsum('j,jm,i->im', x(f), y(f), z(f)) ; 1D, 2D, 1D

        for (f <- 0 until bins) {
          invCovF = invCov(f) // [DK][DK]
          kalmanF = kalman(f) // [DK]

          aux[i] = for (i <- 0 until DK) {
            kalmanFI = kalmanF(i) // scalar
            // np.einsum('j,jm->m', window(f), invCovF) * kalmanFI
            aux[i][m] = np.einsum('j,jm->m', window(f), invCovF) = for (m <- 0 until DK) yield {
              sum(j <- 0 until DK) {
                conj(window(f)(j)) * invCovF(j)(m)
              }
            } * kalmanFI
          }
          invCovF[i][m] = (invCovF[i][m] - aux[i][m]) / alpha
        }

       */

      // XXX TODO --- this is bloody slow; perhaps avoid winBuf multi-indexing
      val _bins   = bins
      val alphaR  = 1.0 / alpha
      val _taps   = taps
      val DK      = numChannels * _taps
      val _winBuf = winBuf          // [numChannels][T][bins C]
      val _invCov = invCov          // [bins][numChannels * taps][numChannels * taps C]
      val aux     = _invCov(_bins)  // [numChannels * taps][numChannels * taps C]
      val _kalman = kalman          // [bins][numChannels * taps C]
      var f_re    = 0
      var f_im    = 1
      var bin     = 0
      while (bin < _bins) {
        val invCovF = _invCov(bin)  // [numChannels * taps][numChannels * taps C]
        val kalmanF = _kalman(bin)  // [numChannels * taps C]
        var i       = 0
        var i_re    = 0
        var i_im    = 1
        // we first store the invCovF gradient (K ỹH R(t-1)) in aux
        while (i < DK) {
          val kalman_re = kalmanF(i_re)
          val kalman_im = kalmanF(i_im)
          val auxI      = aux(i)    // [numChannels * taps C]
          var m     = 0
          var m_re  = 0
          var m_im  = 1
          while (m < DK) {
            var sum_re    = 0.0
            var sum_im    = 0.0
            var tJ = 0
            var j  = 0
            while (tJ < _taps) {
              var chJ = 0
              while (chJ < numChannels) {
                val winChT    = _winBuf(chJ)(tJ) // [bins C]
                val win_re    =  winChT(f_re)
                val win_im    = -winChT(f_im)
                val invCovFJ  = invCovF(j)     // [numChannels * taps C]
                val inv_re    = invCovFJ(m_re)
                val inv_im    = invCovFJ(m_im)
                sum_re += win_re * inv_re - win_im * inv_im
                sum_im += win_re * inv_im + win_im * inv_re
                chJ += 1
                j   += 1
              }
              tJ += 1
            }
            auxI(m_re) = sum_re * kalman_re - sum_im * kalman_im
            auxI(m_im) = sum_re * kalman_im + sum_im * kalman_re
            m    += 1
            m_re += 2
            m_im += 2
          }
          i    += 1
          i_re += 2
          i_im += 2
        }

        // now we incorporate aux into invCovF
        /*var*/ i       = 0
        while (i < DK) {
          val auxI      = aux(i)      // [numChannels * taps C]
          val invCovFI  = invCovF(i)  // [numChannels * taps C]
          var m     = 0
          var m_re  = 0
          var m_im  = 1
          while (m < DK) {
            invCovFI(m_re) = (invCovFI(m_re) - auxI(m_re)) * alphaR
            invCovFI(m_im) = (invCovFI(m_im) - auxI(m_im)) * alphaR
            m    += 1
            m_re += 2
            m_im += 2
          }
          i += 1
        }

        bin  += 1
        f_re += 2
        f_im += 2
      }
    }

    // eq. (16)
    private def updateFilter(): Unit = {
      /*
        filter_taps : [F][D * K][D]   complex
        kalman_gain : [F][D * K]      complex
        prediction  : [F][D]          complex

        self.filter_taps = (
            self.filter_taps +
            np.einsum('fi,fm->fim', self.kalman_gain, np.conjugate(prediction))
        )

       */

      val _bins   = bins
      val _taps   = taps
      val DK      = numChannels * _taps
      val _filter = filter  // [numChannels][bins][numChannels * taps C]
      val _kalman = kalman  // [bins][numChannels * taps C]
      val _pred   = pred    // [numChannels][bins C]
      var f_re    = 0
      var f_im    = 1
      var bin     = 0
      while (bin < _bins) {
        val kalmanF = _kalman(bin)  // [numChannels * taps C]
        var i     = 0
        var i_re  = 0
        var i_im  = 1
        while (i < DK) {
          val kalman_re = kalmanF(i_re)
          val kalman_im = kalmanF(i_im)
          var m = 0
          while (m < numChannels) {
            val filterChF = _filter (m)(bin)
            val pred_re   =  _pred  (m)(f_re)
            val pred_im   = -_pred  (m)(f_im)
            filterChF(i_re) += kalman_re * pred_re - kalman_im * pred_im
            filterChF(i_im) += kalman_re * pred_im + kalman_im * pred_re
            m += 1
          }
          i    += 1
          i_re += 2
          i_im += 2
        }

        bin  += 1
        f_re += 2
        f_im += 2
      }
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
              bins          = newBins
              frameSize     = newBins << 1
              buffersValid  = false
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

          if (buffersValid) {
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
            val I     = numChannels * taps
            val IC    = I << 1

            winBuf    = Array.ofDim(numChannels, T, frameSize)
            pred      = Array.ofDim(numChannels, frameSize)
            psd       = new Array(bins)
            invCov    = Array.tabulate(bins + 1, I, IC) { case (_, i, j) =>
              if ((i << 1) == j) 1.0 else 0.0   // "eye"
            }
            filter    = Array.ofDim(numChannels, bins, IC)
            kalman    = Array.ofDim(bins, IC)

            buffersValid = true
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
                  val t         = taps + delay
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
                      val t         = taps + delay
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
