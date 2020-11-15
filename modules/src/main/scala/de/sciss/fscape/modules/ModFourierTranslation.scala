/*
 *  ModFourierTranslation.scala
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

import de.sciss.fscape.GE
import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _}
import de.sciss.lucre.Txn
import de.sciss.synth.proc.{FScape, Widget}

import scala.Predef.{any2stringadd => _}

object ModFourierTranslation extends Module {
  val name = "Fourier Translation"

  /**
    * Attributes:
    *
    * - `"in"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-type"`: either `0` (normalized) or `1` (relative)
    * - `"gain-db"`: gain factor with respect to gain-type (headroom or factor), in decibels
    * - `"in-imag`: imaginary input (optional)
    * - `"complex-in`: whether we have imaginary input (`1`) or not (`0`)
    * - `"out-imag`: imaginary output (optional)
    * - `"complex-out`: whether we have imaginary output (`1`) or not (`0`)
    * - `"len-mode"`: one of `0` (expand), `1` (truncate)
    * - `"direction"`: one of `0` (forward), `1` (backward)
    */
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 03-Apr-2019
      val in0           = AudioFileIn("in")
      val sr            = in0.sampleRate
      val numFramesIn   = in0.numFrames
      val fileType      = "out-type"    .attr(0)
      val smpFmt        = "out-format"  .attr(2)
      val gainType      = "gain-type"   .attr(1)
      val gainDb        = "gain-db"     .attr(0.0)
      val lenMode       = "len-mode"    .attr(0).clip()
      val inIsComplex   = "complex-in"  .attr(0).clip()
      val outIsComplex  = "complex-out" .attr(0).clip()
      val dir           = "direction"   .attr(0).clip()
      val dirFFT        = dir * -2 + (1: GE)  // bwd = -1, fwd = +1
      val numFramesOut  = (numFramesIn + lenMode).nextPowerOfTwo / (lenMode + (1: GE))
      val numFramesInT  = numFramesIn min numFramesOut
      val gainAmt       = gainDb.dbAmp

      val inT           = in0.take(numFramesInT)
      val inImag = If (inIsComplex) Then {
        AudioFileIn("in-imag")
      } Else {
        DC(0.0)
      }
      val inImagT = inImag.take(numFramesInT)
      val inC = inT zip inImagT
      val fft = Fourier(inC, size = numFramesInT,
        padding = numFramesOut - numFramesInT, dir = dirFFT)

      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFramesOut, label)
        ()
      }

      def applyGain(x: GE) =
        If (gainType sig_== 0) Then {
          val rsmpBuf   = BufferDisk(x)
          val rMax      = RunningMax(Reduce.max(x.abs))
          mkProgress(rMax, "analyze")
          val maxAmp    = rMax.last
          val div       = maxAmp + (maxAmp sig_== 0.0)
          val gainAmtN  = gainAmt / div
          rsmpBuf * gainAmtN

        } Else {
          x * gainAmt
        }

      If (outIsComplex) Then {
        val fftN      = applyGain(fft)
        // XXX TODO --- this doesn't play nicely with mce
        // val fftNU     = UnzipWindow(fftN)
        // val outN      = fftNU.out(0)
        // val outImagN  = fftNU.out(1)
        val outN      = ResizeWindow(fftN, 2, start = 0, stop = -1)
        val outImagN  = ResizeWindow(fftN, 2, start = 1, stop =  0)
        val writtenRe = AudioFileOut("out", outN, fileType = fileType,
          sampleFormat = smpFmt, sampleRate = sr)
        mkProgress(writtenRe, "write-real")
        val writtenIm = AudioFileOut("out-imag", outImagN, fileType = fileType,
          sampleFormat = smpFmt, sampleRate = sr)
        mkProgress(writtenIm, "write-imag")

      } Else {
        // XXX TODO --- this doesn't play nicely with mce
        // val outN    = applyGain(fft.complex.real)
        val re      = ResizeWindow(fft, 2, start = 0, stop = -1)
        val outN    = applyGain(re)
        val written = AudioFileOut("out", outN, fileType = fileType,
          sampleFormat = smpFmt, sampleRate = sr)
        mkProgress(written, "write")
      }
      ()
    }
    f
  }

  def ui[T <: Txn[T]]()(implicit tx: T): Widget[T] = {
    import de.sciss.lucre.expr.ExImport._
    import de.sciss.lucre.expr.graph._
    import de.sciss.lucre.swing.graph._
    val w = Widget[T]()
    import de.sciss.synth.proc.MacroImplicits._
    w.setGraph {
      // version: 02-Apr-2019
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in    = AudioFileIn()
      in.value <--> Artifact("run:in")

      val inIm = AudioFileIn()
      //inIm.formatVisible = false
      inIm.value <--> Artifact("run:in-imag")
      val ggInIsComplex = CheckBox("Input [Im]:")
      ggInIsComplex.selected <--> "run:complex-in".attr(false)
      inIm.enabled = ggInIsComplex.selected()

      val out   = AudioFileOut()
      out.value         <--> Artifact("run:out")
      out.fileType      <--> "run:out-type".attr(0)
      out.sampleFormat  <--> "run:out-format".attr(2)

      val outIm  = AudioFileOut()
      outIm.fileTypeVisible     = false
      outIm.sampleFormatVisible = false
      outIm.value <--> Artifact("run:out-imag")
      val ggOutIsComplex = CheckBox("Output [Im]:")
      ggOutIsComplex.selected <--> "run:complex-out".attr(false)
      outIm.enabled = ggOutIsComplex.selected()

      val ggGain = DoubleField()
      ggGain.unit = "dB"
      ggGain.min  = -180.0
      ggGain.max  = +180.0
      ggGain.value <--> "run:gain-db".attr(0.0)

      val ggGainType = ComboBox(
        List("Normalized", "Immediate")
      )
      ggGainType.index <--> "run:gain-type".attr(1)

      val ggDir = ComboBox(List("Forward", "Backward (Inverse)"))
      ggDir.index <--> "run:direction".attr(0)

      val ggLenMode = ComboBox(List(
        "Expand to 2^n", "Truncate to 2^n"
      ))
      ggLenMode.index <--> "run:len-mode".attr(0)

      def mkLabel(text: String) = {
        val l = Label(text)
        l.hAlign = Align.Trailing
        l
      }

      def left(c: Component*): Component = {
        val f   = FlowPanel(c: _*)
        f.align = Align.Leading
        f.vGap  = 0
        f
      }

      def right(c: Component*): Component = {
        val f   = FlowPanel(c: _*)
        f.align = Align.Trailing
        f.hGap  = 0
        f.vGap  = 0
        f
      }

      val p = GridPanel(
        mkLabel("Input [Re]:" ), in,
        right(ggInIsComplex), inIm,
        mkLabel("Output [Re]:"), out,
        right(ggOutIsComplex), outIm,
        mkLabel("Gain:"), left(ggGain, ggGainType),
        Label(" "), Empty(),
        mkLabel("Direction:"), left(ggDir),
        mkLabel("FFT Length:"), left(ggLenMode)
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
