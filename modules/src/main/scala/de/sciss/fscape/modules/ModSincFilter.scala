/*
 *  ModSincFilter.scala
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
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.Txn
import de.sciss.synth.proc.Widget

import scala.Predef.{any2stringadd => _}

object ModSincFilter extends Module {
  val name = "Sinc Filter"

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 24-Mar-2020
      val in        = AudioFileIn("in")
      val sr        = in.sampleRate
      val f1        = "freq1".attr( 500.0)
      val f2        = "freq2".attr(1000.0)
      //val roll      = "roll".attr(0.0)
      val tpe       = "filter-type".attr(0).clip(0, 3)
      val numZero   = "zero-crossings".attr(6).max(1)
      val kaiser    = "kaiser".attr(6.0).max(1.0)

      val fileType      = "out-type"    .attr(0)
      val smpFmt        = "out-format"  .attr(2)
      val gainType      = "gain-type"   .attr(0) // 1
      val gainDb        = "gain-db"     .attr(0.0)

      val f1N       = f1 / sr
      val f2N       = f2 / sr
      val fltLenH   = (numZero / f1N).ceil.max(1)
      val fltLen    = fltLenH * 2 // - 1

      val inLen     = in.numFrames
      val convLen0  = fltLen + fltLen - 1 // estimate
      val fftLen    = convLen0.nextPowerOfTwo
      val chunkLen  = fftLen - fltLen + 1
      val convLen   = chunkLen + fltLen - 1
      val inF       = Real1FFT(in, chunkLen, fftLen - chunkLen, mode = 1)

      val flt0: GE = If (tpe sig_== 0) Then {
        // low-pass
        GenWindow.Sinc(fltLen, param = f1N) * f1N
      } ElseIf (tpe sig_== 1) Then {
        // high-pass
        GenWindow.Sinc(fltLen, param = 0.5) * 0.5 -
        GenWindow.Sinc(fltLen, param = f1N) * f1N
      } ElseIf (tpe sig_== 2) Then {
        // band-pass
        GenWindow.Sinc(fltLen, param = f2N) * f2N -
        GenWindow.Sinc(fltLen, param = f1N) * f1N
      } Else {
        // band-stop
        GenWindow.Sinc(fltLen, param = 0.5) * 0.5 -
        GenWindow.Sinc(fltLen, param = f2N) * f2N +
        GenWindow.Sinc(fltLen, param = f1N) * f1N
      }
      val win = GenWindow.Kaiser(fltLen, param = kaiser)
      val flt = (flt0 * win).take(fltLen)
      //val flt  = RepeatWindow(flt1, fltLen, (inLen / chunkLen).ceil)
      //Plot1D(flt, fltLen)

      val fltF0     = Real1FFT(flt, fltLen, fftLen - fltLen, mode = 1)
      // N.B.: the output in `mode = 1` has length `fftLen + 2`
      val fltF      = RepeatWindow(fltF0, fftLen + 2, (inLen / chunkLen).ceil)
      val convF     = inF.complex * fltF
      val conv0     = Real1IFFT(convF, fftLen, mode = 1)
      val conv      = OverlapAdd(conv0, size = convLen, step = chunkLen)
      val sig0      = conv.drop(fltLenH).take(inLen)

      val numFramesOut  = inLen
      val gainAmt       = gainDb.dbAmp

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

      val written = AudioFileOut("out", sig, fileType = fileType,
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
    import de.sciss.synth.proc.MacroImplicits._
    w.setGraph {
      // version: 17-Jun-2019
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
      ggGainType.index <--> "run:gain-type".attr(0) // 1

      val ggFilterType = ComboBox(
        List("Low Pass", "High Pass", "Band Pass", "Band Stop")
      )
      ggFilterType.index <--> "run:filter-type".attr(0)
      val idxFilter = ggFilterType.index()

      def mkLabel(text: Ex[String]) = {
        val l = Label(text)
        l.hAlign = Align.Trailing
        l
      }

      val ggF1      = DoubleField()
      ggF1.unit     = "Hz"
      ggF1.min      = 0.0
      ggF1.max      = 192000.0
      ggF1.value <--> "run:freq1".attr(500.0)

      val txtF1     = (Seq("Cutoff Freq.:", "Lower Freq.:"): Ex[Seq[String]])
        .applyOption(idxFilter >> 1).getOrElse("?")
      val lbF1      = mkLabel(txtF1)

      val ggF2      = DoubleField()
      ggF2.unit     = "Hz"
      ggF2.min      = 0.0
      ggF2.max      = 192000.0
      ggF2.value <--> "run:freq2".attr(1000.0)

      val hasF2     = idxFilter >= 2

      ggF2.enabled  = hasF2

      val lbF2      = mkLabel("Upper Freq.:")
      lbF2.enabled  = hasF2

      val ggZero    = IntField()
      ggZero.min    = 1
      ggZero.max    = 100
      ggZero.value <--> "run:zero-crossings".attr(6)

      val ggKaiser  = DoubleField()
      ggKaiser.min  =  1.0
      ggKaiser.max  = 16.0
      ggKaiser.value <--> "run:kaiser".attr(6.0)

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
        mkLabel("Type:"), left(ggFilterType),
        lbF1, left(ggF1),
        lbF2, left(ggF2),
        mkLabel("Zero Crossings:"), left(ggZero),
        mkLabel("Kaiser β:"), left(ggKaiser),
        Label(" ") ,Empty(),
        mkLabel(""), Label(
          "<html><b>Note:</b> Auto-gain not implemented yet, use 'Normalized'.")
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