/*
 *  ModInverseFilter.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.modules

import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Widget

import scala.Predef.{any2stringadd => _}

object ModInverseFilter extends Module {
  val name = "Inverse Filter"

  /**
    * Attributes:
    *
    * - `"in"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-db"`: input boost factor (before entering limiter), in decibels
    * - `"ceil-db"`: limiter clipping level, in decibels
    * - `"atk-ms"`: limiter attack time in milliseconds, with respect to -60 dB point
    * - `"rls-ms"`: limiter release time in milliseconds, with respect to -60 dB point
    * - `"sync-chans"`: whether to synchronise limiter gain control across input channels (1) or not (0)
    */
  def apply[S <: Sys[S]]()(implicit tx: S#Tx): FScape[S] = {
    val f = FScape[S]()
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    import de.sciss.fscape.GE
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 03-Oct-2019
      val in            = AudioFileIn("in")
      val sr            = in.sampleRate

      val fileType      = "out-type"      .attr(0)
      val smpFmt        = "out-format"    .attr(2)
      val gainType      = "gain-type"     .attr(0) // 1
      val gainDb        = "gain-db"       .attr(0.0)
      val maxBoostDb    = "max-boost-db"  .attr(200.0)
      val maxLengthMs   = "max-length-ms" .attr(200.0)

      val inSz          = in.numFrames
      val fftSize       = inSz.nextPowerOfTwo * 2
      val fft           = Real1FFT(in, size = inSz, padding = fftSize - inSz, mode = 1)
      val mag           = fft.complex.mag
      val inv           = mag.max(-maxBoostDb.dbAmp).reciprocal zip DC(0.0)

      def mkMinPhase(fft: GE): GE = {
        val log         = fft.complex.log
        val logC        = log max -320.0 // -80
        val cep0        = Complex1IFFT  (in  = logC, size = fftSize, padding = 0)
        val cep         = cep0 * (1.0/fftSize)

        val crr = +1; val cri = +1
        val clr =  0; val cli =  0
        val ccr = +1; val cci = -1
        val car =  0; val cai =  0

        val cepOut      = FoldCepstrum  (in = cep, size = fftSize,
          crr = crr, cri = cri, clr = clr, cli = cli,
          ccr = ccr, cci = cci, car = car, cai = cai)

        val freq0       = Complex1FFT   (in = cepOut, size = fftSize)
        val freq1       = freq0 * fftSize
        val freq        = freq1 // ComplexUnaryOp(in = freq1, op = ComplexUnaryOp.Conj)
        val fftOut      = freq.complex.exp

        // 'synthesis'
        val outW        = Real1FullIFFT(in = fftOut, size = fftSize)
        outW
      }

      val time          = mkMinPhase(inv)
      val maxLength     = (maxLengthMs * 0.001 * sr).floor
      val numFramesOut  = fftSize min maxLength
      val sig0          = time.take(numFramesOut) * Line(1.0, 0.0, numFramesOut)

      val gainAmt       = gainDb.dbAmp

      def mkProgress(x: GE, label: String) =
        ProgressFrames(x, numFramesOut, label)

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

      val written = AudioFileOut("out", sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sr)
      ProgressFrames(written, numFramesOut)
    }
    f
  }

  def ui[S <: Sys[S]]()(implicit tx: S#Tx): Widget[S] = {
    import de.sciss.lucre.expr.ExImport._
    import de.sciss.lucre.expr.graph._
    import de.sciss.lucre.swing.graph._
    val w = Widget[S]()
    import de.sciss.synth.proc.MacroImplicits._
    w.setGraph {
      // version: 03-Oct-2019
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in    = AudioFileIn()
      in.value <--> Artifact("run:in")
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
      ggGainType.index <--> "run:gain-type".attr(0)

      val ggMaxLen       = DoubleField()
      ggMaxLen.unit      = "ms"
      ggMaxLen.min       = 0.1
      ggMaxLen.max       = 10000.0
      ggMaxLen.decimals  = 1
      ggMaxLen.value <--> "run:max-length-ms".attr(200.0)

      val ggMaxBoost    = DoubleField()
      ggMaxBoost.unit      = "dB"
      ggMaxBoost.min       =   0.0
      ggMaxBoost.max       = 320.0
      ggMaxBoost.decimals  = 1
      ggMaxBoost.value <--> "run:max-boost-db".attr(200.0)

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
        mkLabel("Input:" ), in,
        mkLabel("Output:"), out,
        mkLabel("Gain:"), left(ggGain, ggGainType),
        Label(" "), Empty(),
        mkLabel("Max. Length:"), left(ggMaxLen),
        mkLabel("Max. Band Boost:"), left(ggMaxBoost),
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
