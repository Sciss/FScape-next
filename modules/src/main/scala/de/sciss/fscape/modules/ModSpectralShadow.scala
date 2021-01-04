/*
 *  ModSpectralShadow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.modules

import de.sciss.fscape.GE
import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
import de.sciss.lucre.Txn
import de.sciss.proc.{FScape, Widget}

import scala.Predef.{any2stringadd => _}

object ModSpectralShadow extends Module {
  val name = "Spectral Shadow"

  /**
    * Attributes:
    *
    * - `"in-fg"`: audio file input
    * - `"in-bg"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-type"`: either `0` (normalized) or `1` (relative)
    * - `"gain-db"`: gain factor with respect to gain-type (headroom or factor), in decibels
    * - `"blur-time"`: in seconds
    * - `"thresh-noise-db"`:
    * - `"thresh-distance-db"`:
    */
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // adapted from Mäanderungen
      // hhr 29-may-2019

      val fInFg                 = "in-fg"
      val fInBg                 = "in-bg"
      val fOut                  = "out"
      val maskBlurTime          = "blur-time"         .attr(2.0)
      val maskBlurFreq          = "blur-freq"         .attr(200.0)
      val maskThreshNoiseDb     = "thresh-noise-db"   .attr(-60.0)
      val maskThreshDistanceDb  = "thresh-distance-db".attr( 12.0)

      val gainType  = "gain-type" .attr(1)
      val gainDb    = "gain-db"   .attr(0.0)
      val gainAmt   = gainDb.dbAmp
      val fileType  = "out-type"  .attr(0)
      val smpFmt    = "out-format".attr(2)

      def mkBgIn() = AudioFileIn(fInBg)

      val inFg      = AudioFileIn(fInFg)
      val inBg      = mkBgIn()
      val sr        = inFg.sampleRate
      val numFrames = inFg.numFrames min inBg.numFrames

      val fMin      = 50.0
      val winSizeH  = ((sr / fMin) * 3).ceil /* .toInt */ / 2
      val winSize   = winSizeH * 2
      val stepSize  = winSize / 2
      val fftSizeH  = winSize.nextPowerOfTwo
      val fftSize   = fftSizeH * 2

      val in1S      = Sliding(inFg, size = winSize, step = stepSize)
      val in2S      = Sliding(inBg, size = winSize, step = stepSize)
      val in1W      = in1S * GenWindow.Hann(winSize)
      val in2W      = in2S * GenWindow.Hann(winSize)

      val in1F      = Real1FFT(in1W, size = winSize, padding = fftSize - winSize)
      val in2F      = Real1FFT(in2W, size = winSize, padding = fftSize - winSize)

      val in1Mag    = in1F.complex.mag
      val in2Mag    = in2F.complex.mag

      val blurTime  = ((maskBlurTime * sr) / stepSize) .ceil // .toInt
      val blurFreq  = (maskBlurFreq / (sr / fftSize)).ceil // .toInt
      val columns   = blurTime * 2 + 1
      def post      = DC(0.0).take(blurTime * fftSizeH)
      val in1Pad    = in1Mag ++ post
      val in2Pad    = in2Mag ++ post

      val mask      = Masking2D(fg = in1Pad, bg = in2Pad, rows = fftSizeH, columns = columns,
        threshNoise = maskThreshNoiseDb.dbAmp, threshMask = (-maskThreshDistanceDb).dbAmp,
        blurRows = blurFreq, blurColumns = blurTime)

      //    Plot1D(mask.drop(fftSizeH * 16).ampDb, size = fftSizeH)

      // RunningMax(mask < 1.0).last.poll(0, "has-filter?")

      val maskC     = mask zip DC(0.0)
      val fltSym    = (Real1IFFT(maskC, size = fftSize) / fftSizeH).drop(blurTime * fftSize)

      //    Plot1D(flt.drop(fftSize * 16), size = fftSize)

      val fftSizeCep  = fftSize * 2
      val fltSymR     = {
        val r   = RotateWindow(fltSym, fftSize, fftSizeH)
        val rr  = ResizeWindow(r  , fftSize, stop = fftSize)
        val rrr = RotateWindow(rr, fftSizeCep, -fftSizeH)
        rrr
      }
      //    val fltF        = Real1FullFFT(in = fltSymR, size = fftSize, padding = fftSize)
      val fltF        = Real1FullFFT(in = fltSymR, size = fftSizeCep, padding = 0)
      val fltFLogC    = fltF.complex.log.max(-320)

      val cep         = Complex1IFFT(in = fltFLogC, size = fftSizeCep) / fftSize
      val cepOut      = FoldCepstrum(in = cep, size = fftSizeCep,
        crr = +1, cri = +1, clr = 0, cli = 0,
        ccr = +1, cci = -1, car = 0, cai = 0)

      val fltMinF     = Complex1FFT(in = cepOut, size = fftSizeCep) * fftSize
      val fltMinFExpC = fltMinF.complex.exp
      val fltMin0     = Real1FullIFFT (in = fltMinFExpC, size = fftSizeCep)
      val fltMin      = ResizeWindow(fltMin0, fftSizeCep, stop = -fftSize)

      val bg        = mkBgIn() // .take(numFrames)
      val bgS       = Sliding(bg, size = winSize, step = stepSize)
      val bgW       = bgS * GenWindow.Hann(winSize)
      val convSize  = (winSize + fftSize - 1).nextPowerOfTwo
      val bgF       = Real1FFT(bgW /* RotateWindow(bgW, winSize, -winSizeH) */, winSize,
                               convSize - winSize)
      val fltMinFF  = Real1FFT(fltMin, fftSize, convSize - fftSize)
      val convF     = bgF.complex * fltMinFF
      val conv      = Real1IFFT(convF, convSize) * convSize
      val convLap   = OverlapAdd(conv, convSize, stepSize).take(numFrames)
      val sig0      = convLap * 0.5 // XXX TODO --- where does this gain factor come from?

      val numFramesOut = numFrames

      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFramesOut, label)
        ()
      }

      def applyGain(x: GE) =
        If (gainType sig_== 0) Then {
          val buf       = BufferDisk(x)
          val rMax      = RunningMax(Reduce.max(x.abs))
          mkProgress(rMax, "analyze")
          val maxAmp    = rMax.last
          val div       = maxAmp + (maxAmp sig_== 0.0)
          val gainAmtN  = gainAmt / div
          buf * gainAmtN

        } Else {
          x * gainAmt
        }

      val sig = applyGain(sig0)

      val written = AudioFileOut(fOut, sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sr)
      mkProgress(written, "write")
    }
    f
  }

  def ui[T <: Txn[T]]()(implicit tx: T): Widget[T] = {
    import de.sciss.lucre.expr.ExImport._
    import de.sciss.lucre.expr.graph._
    import de.sciss.lucre.swing.graph._
    val w = Widget[T]()
    import de.sciss.proc.MacroImplicits._
    w.setGraph {
      // version: 10-Apr-2020
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val inFg    = AudioFileIn()
      inFg.value <--> Artifact("run:in-fg")
      val inBg    = AudioFileIn()
      inBg.value <--> Artifact("run:in-bg")
      val out   = AudioFileOut()
      out.value         <--> Artifact("run:out")
      out.fileType      <--> "run:out-type".attr(0)
      out.sampleFormat  <--> "run:out-format".attr(2)

      val ggGain = DoubleField()
      ggGain.unit = "dB"
      ggGain.min  = -180.0
      ggGain.max  = +180.0
      ggGain.value <--> "run:gain-db".attr(0.0)

      val ggGainType = ComboBox(
        List("Normalized", "Immediate")
      )
      ggGainType.index <--> "run:gain-type".attr(1)

      val ggBlurTime = DoubleField()
      ggBlurTime.unit = "s"
      ggBlurTime.min  = 0.01
      ggBlurTime.max  = 100.0
      ggBlurTime.value <--> "run:blur-time".attr(2.0)

      val ggBlurFreq = DoubleField()
      ggBlurFreq.unit = "Hz"
      ggBlurFreq.min  = 1.0
      ggBlurFreq.max  = 10000.0
      ggBlurFreq.value <--> "run:blur-freq".attr(200.0)

      val ggThreshNoise = DoubleField()
      ggThreshNoise.unit = "dB"
      ggThreshNoise.min  = -320.0
      ggThreshNoise.max  = 0.0
      ggThreshNoise.value <--> "run:thresh-noise-db".attr(-60.0)

      val ggThreshDist = DoubleField()
      ggThreshDist.unit = "dB"
      ggThreshDist.min  = 0.0
      ggThreshDist.max  = 320.0
      ggThreshDist.value <--> "run:thresh-distance-db".attr(12.0)

      def mkLabel(text: String) = {
        val l = Label(text)
        l.hAlign = Align.Trailing
        l
      }

      def left(c: Component*): Component = {
        val f = FlowPanel(c: _*)
        f.align = Align.Leading
        f.vGap = 0
        f
      }

      val p = GridPanel(
        mkLabel("Foreground Input:" ), inFg,
        mkLabel("Background Input:" ), inBg,
        mkLabel("Output:"), out,
        mkLabel("Gain:"), left(ggGain, ggGainType),
        Label(" "), Empty(),
        mkLabel("Blur Time:"  ), left(ggBlurTime    ),
        mkLabel("Blur Freq:"  ), left(ggBlurFreq    ),
        mkLabel("Noise Floor:"), left(ggThreshNoise ),
        mkLabel("Fg/Bg Distance:"), left(ggThreshDist  ),
        Label(" "), Label(" "),
      )
      p.columns = 2
      p.hGap    = 8
      p.compact = true

      val ggRender  = Button(" Render ")
      val ggCancel  = Button(" X ")
      ggCancel.tooltip = "Cancel Rendering"
      val pb        = ProgressBar()
      ggRender.clicked ---> r.run
      ggCancel.clicked ---> r.stop
      val stopped = (r.state sig_== 0) || (r.state > 3)
      ggRender.enabled = stopped
      ggCancel.enabled = !stopped
      pb.value = (r.progress * 100).toInt
      val bot = BorderPanel(
        center  = pb,
        east    = {
          val f = FlowPanel(ggCancel, ggRender)
          f.vGap = 0
          f
        }
      )
      bot.vGap = 0
      val bp = BorderPanel(
        north = p,
        south = bot
      )
      bp.vGap = 8
      bp.border = Border.Empty(8, 8, 0, 4)
      bp
    }
    w
  }
}
