/*
 *  ModFreqShift.scala
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
import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
import de.sciss.lucre.Txn
import de.sciss.numbers.Implicits._
import de.sciss.proc.{FScape, Widget}

import scala.Predef.{any2stringadd => _}

object ModFreqShift extends Module {
  val name = "Frequency Shift"

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 24-Mar-2020
      // TODO: anti-aliasing filter
      // TODO: compensate for filter gain

      val in0         = AudioFileIn("in")
      val sr          = in0.sampleRate
      val numFramesIn = in0.numFrames
      val fileType    = "out-type"  .attr(0)
      val smpFmt      = "out-format".attr(2)
      val gainType    = "gain-type" .attr(1)
      val gainDb      = "gain-db"   .attr(0.0)
      val gainAmt     = gainDb.dbAmp

      val freqShiftHz = "shift-freq".attr(300.0)
      val freqShiftN  = freqShiftHz / sr

      val lpFreqN     = 0.245 // Fs/4 minus roll-off
      val lpWinSz     = 1024
      val lpWinSzH    = lpWinSz >> 1
      val lpSinc      = GenWindow.Sinc  (lpWinSz, param = lpFreqN)
      val lpSincW     = GenWindow.Kaiser(lpWinSz, param = 8.0) * lpSinc
      //Plot1D(lpSincW, size = 1024)

      val modWSin     = SinOsc(0.25, phase = 0.0)
      val modWCos     = SinOsc(0.25, phase = math.Pi/2)
      //Plot1D(modCos, 16)

      val fltRe       = lpSincW * modWSin
      val fltIm       = lpSincW * modWCos

      val fftSize     = (lpWinSz << 1).nextPowerOfTwo
      val fftSizeC    = fftSize << 1
      val inWinSz     = fftSize /* + 1 */ - lpWinSz

      val inFT    = Complex1FFT(in0 zip DC(0.0), size = inWinSz, padding = fftSize - inWinSz)
      val fltFT   = Complex1FFT(fltRe zip fltIm, size = lpWinSz, padding = fftSize - lpWinSz)
      // avoid running the sinc and fft repeatedly
      val numFFTs = (numFramesIn / inWinSz).ceil
      val fltFTR  = RepeatWindow(fltFT.take(fftSizeC), fftSizeC, num = numFFTs)
      val convFT  = inFT.complex * fltFTR
      val conv0   = Complex1IFFT(convFT, size = fftSize)
      val conv    = OverlapAdd(conv0, size = fftSizeC, step = inWinSz << 1)

      val modSSin = SinOsc(freqShiftN, phase = 0.0)
      val modSCos = SinOsc(freqShiftN, phase = math.Pi/2)

      val convRe  = ResizeWindow(conv, size = 2, start = 0, stop = -1)
      val convIm  = ResizeWindow(conv, size = 2, start = +1, stop = 0)

      // shift up or down
      val shifted = (convRe * modSCos + convIm * modSSin).drop(lpWinSzH).take(numFramesIn)

      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFramesIn, label)
        ()
      }

      val sig0: GE = shifted

      val sig = If (gainType sig_== 0) Then {
        val sig0Buf   = BufferDisk(sig0)
        val rMax      = RunningMax(Reduce.max(sig0.abs))
        mkProgress(rMax, "analyze")
        val maxAmp    = rMax.last
        val div       = maxAmp + (maxAmp sig_== 0.0)
        val gainAmtN  = gainAmt / div
        sig0Buf * gainAmtN

      } Else {
        sig0 * gainAmt
      }

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
    import de.sciss.proc.MacroImplicits._
    w.setGraph {
      // version: 07-Apr-2019
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
      ggGainType.index <--> "run:gain-type".attr(1)

      val ggShift       = DoubleField()
      ggShift.unit      = "Hz"
      ggShift.min       = -192000.0
      ggShift.max       = +192000.0
      ggShift.value <--> "run:shift-freq".attr(300.0)

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
        mkLabel("Shift:"), left(ggShift),
        Label(" "), Label(" "),
        mkLabel(""), Label("<html><b>Note:</b> Anti-aliasing not yet implemented.")
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
