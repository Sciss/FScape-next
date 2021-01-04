/*
 *  ModConvolution.scala
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

object ModConvolution extends Module {
  val name = "Convolution"

  /**
    * Attributes:
    *
    * - `"in"`: audio file input
    * - `"impulse"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-type"`: either `0` (normalized) or `1` (relative)
    * - `"gain-db"`: gain factor with respect to gain-type (headroom or factor), in decibels
    * - `"file-len"`:
    * - `"morph"`: whether to chop up the impulse into multiple piece that are interpolated (Boolean)
    * - `"morph-num"`: number of pieces or sub-impulses. the impulse response is evenly divided into this number
    * - `"morph-win"`: the stepping size in interpolation in milliseconds.
    * - `"min-phase"`: whether to use minimum phase transformation (Boolean)
    */
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 11-Dec-2020

      val in        = AudioFileIn("in")
      val ir        = AudioFileIn("impulse")

      val minPhase  = "min-phase" .attr(0)
      val morph     = "morph"     .attr(0).clip(0, 1)     // 0 -- off, 1 -- on
      val morphNum  = "morph-num" .attr(2).max(1)
      // TODO: add 'norm' option
      // val morphNorm = "morph-norm".attr(0).clip(0, 1)  // 0 -- off, 1 -- on
      val morphWin  = "morph-win" .attr(20.0)             // milliseconds
      val fileLenTpe= "file-len"  .attr(1).clip(0, 1)     // 0 -- input, 1 -- input + IR - 1

      val gainType  = "gain-type" .attr(1)
      val gainDb    = "gain-db"   .attr(0.0)
      val gainAmt   = gainDb.dbAmp
      val fileType  = "out-type"  .attr(0)
      val smpFmt    = "out-format".attr(2)

      val sr            = in.sampleRate
      val numFrames     = in.numFrames
      val irLength      = ir.numFrames
      val irNum         = (1 - morph) + morph * morphNum
      val kernelLen     = (irLength / irNum).toInt.max(1)
      val numFramesOut  = numFrames + (kernelLen - 1) * fileLenTpe
      val morphStep    = (morphWin * 0.001 * ir.sampleRate).toInt.clip(1, kernelLen)

      val kernel0 = If (morph) Then {
        val mul         = morphStep * morphNum
        val repeats     = (numFrames + mul - 1) / mul
        val ir1         = BufferMemory(ir    , kernelLen)
        val ir2         =              ir.drop(kernelLen) ++ DC(0.0).take(kernelLen)
        val irRepeat1   = RepeatWindow(ir1, size = kernelLen, num = repeats)
        val irRepeat2   = RepeatWindow(ir2, size = kernelLen, num = repeats)
        val fadeW       = LFSaw(1.0/repeats) * 0.5 + 0.5
        val fadeWR      = RepeatWindow(fadeW, 1, kernelLen)
        val fade        = irRepeat1 * (1 - fadeWR) + irRepeat2 * fadeWR
        fade

      } Else {
        ir
      }

      val kernel = If (minPhase) Then {
        val fftSize     = kernelLen.nextPowerOfTwo
        val fftSizeCep  = fftSize << 1
        val fftIn       = ResizeWindow(kernel0, kernelLen, stop = fftSizeCep - kernelLen)
        val fltF        = Real1FullFFT(in = fftIn, size = fftSizeCep, padding = 0)
        val fltFLogC    = fltF.complex.log.max(-320)

        val cep         = Complex1IFFT(in = fltFLogC, size = fftSizeCep) / fftSize
        val cepOut      = FoldCepstrum(in = cep, size = fftSizeCep,
          crr = +1, cri = +1, clr = 0, cli = 0,
          ccr = +1, cci = -1, car = 0, cai = 0)

        val fltMinF     = Complex1FFT(in = cepOut, size = fftSizeCep) * fftSize
        val fltMinFExpC = fltMinF.complex.exp
        val fltMin0     = Real1FullIFFT (in = fltMinFExpC, size = fftSizeCep)
        val fltMin      = ResizeWindow(fltMin0, fftSizeCep, stop = kernelLen - fftSizeCep)
        fltMin

      } Else {
        kernel0
      }

      val kernelUpdate = Metro(morphStep, 1) * morph

      val cv = Convolution(
        in,
        kernel,
        kernelLen     = kernelLen,
        kernelUpdate  = kernelUpdate,
      )
      val sig0 = cv.take(numFramesOut)

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
    import de.sciss.lucre.expr.graph._
    import de.sciss.lucre.expr.ExImport._
    import de.sciss.lucre.swing.graph._
    val w = Widget[T]()
    import de.sciss.proc.MacroImplicits._
    w.setGraph {
      // version: 11-Dec-2020
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in     = AudioFileIn()
      in.value <--> Artifact("run:in")
      val ir     = AudioFileIn()
      ir.value <--> Artifact("run:impulse")
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

      val ggFileLen = ComboBox(Seq("Input", "Input + Impulse"))
      ggFileLen.index <--> "run:file-len".attr(1)

      val ggMinPhase = CheckBox()
      ggMinPhase.selected <--> "run:min-phase".attr(false)

      val ggMorph = CheckBox()
      ggMorph.selected <--> "run:morph".attr(false)

      val ggMorphNum = IntField()
      ggMorphNum.min  = 1
      ggMorphNum.max  = Int.MaxValue
      ggMorphNum.value <--> "run:morph-num".attr(2)

      val ggMorphWin = DoubleField()
      ggMorphWin.unit = "ms"
      ggMorphWin.min  = 0.1
      ggMorphWin.max  = 60000.0
      ggMorphWin.step = 1.0
      ggMorphWin.value <--> "run:morph-win".attr(20.0)

      ggMorphNum.enabled = ggMorph.selected()
      ggMorphWin.enabled = ggMorph.selected()

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
        mkLabel("Impulse Response:" ), ir,
        mkLabel("Output:"), out,
        mkLabel("Gain:"), left(ggGain, ggGainType, mkLabel("  File Length:"), ggFileLen),
        Label(" "), Empty(),
        mkLabel("Morph:"  ), left(ggMorph, mkLabel("  No. of Pieces:"), ggMorphNum,
          mkLabel("  Window Step:"), ggMorphWin),
        mkLabel("Minimum Phase:"), left(ggMinPhase),
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
