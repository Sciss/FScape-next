/*
 *  ModSignalGenerator.scala
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

import de.sciss.lucre.Txn
import de.sciss.synth.proc.{FScape, Widget}

import scala.Predef.{any2stringadd => _}

object ModSignalGenerator extends Module {
  val name = "Signal Generator"

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 05-Apr-2020
      val tpe         = "signal-type" .attr(0)
      val dur         = "dur"         .attr(10.0)
      val amp0        = "amp"         .attr(1.0)
      val fileType    = "out-type"    .attr(0)
      val smpFmt      = "out-format"  .attr(2)
      val sampleRate  = "sampleRate"  .attr(44100.0)
      //val numChannels = "num-channels".attr(1).max(1)

      val freq        = "freq"      .attr(440.0)
      val phase0      = "phase"     .attr(0.0)
      val fadeIn      = "fade-in"   .attr(0.0).max(0.0)
      val fadeOut     = "fade-out"  .attr(0.0).max(0.0)
      val freqN       = freq / sampleRate
      val amp         = amp0 // XXX TODO: channels

      val numFrames   = (dur     * sampleRate).toLong
      val fadeInFr    = (fadeIn  * sampleRate).toLong.min(numFrames)
      val fadeOutFr   = (fadeOut * sampleRate).toLong.min(numFrames + fadeInFr)

      // numFrames.poll(0, "numFrames")

      val raw = If (tpe sig_== 0) Then {
        val phaseRad = phase0 * (2*math.Pi)
        SinOsc(freqN, phaseRad) * amp
      } ElseIf (tpe sig_== 1) Then {
        LFSaw(freqN, phase0 % 1.0) * amp
      } ElseIf (tpe sig_== 2) Then {
        Metro(freqN.reciprocal) * amp
      } ElseIf (tpe sig_== 3) Then {
        DC(amp)
      } Else {
        WhiteNoise(amp)
      }

      val lvl0    = ValueSeq(0.0, 1.0, 1.0, 0.0)
      val lens0   = fadeInFr ++ (numFrames - fadeInFr - fadeOutFr) ++ fadeOutFr
      val noFdOut = fadeOutFr sig_== 0
      val lvl1    = lvl0 .take(4 - noFdOut)
      val len1    = lens0.take(3 - noFdOut)
      val noFdIn  = fadeInFr sig_== 0
      val lvl     = lvl1.drop(noFdIn)
      val len     = len1.drop(noFdIn)

      val env     = DEnvGen(levels = lvl, lengths = len)

      val sig     = raw.take(numFrames) * env

      val written = AudioFileOut("out", sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sampleRate)
      ProgressFrames(written, numFrames)
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
      // version 06-Apr-2019
      val r = Runner("run")
      val m = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val out = AudioFileOut()
      out.sampleRateVisible = true
      out.value        <--> Artifact("run:out")
      out.fileType     <--> "run:out-type".attr(0)
      out.sampleFormat <--> "run:out-format".attr(2)
      out.sampleRate   <--> "run:sampleRate".attr(44100.0)

      val ggGain = DoubleField()
      ggGain.min      = 0.0
      ggGain.decimals = 3
      ggGain.step     = 0.01
      ggGain.value <--> "run:amp".attr(1.0)

      def mkDurField() = {
        val gg  = DoubleField()
        gg.min       = 0.0
        gg.decimals  = 4
        gg.max       = 3600.0
        gg.unit = "s  "
        gg
      }

      val ggDur  = mkDurField()
      ggDur.value <--> "run:dur".attr(10.0)

      val ggFadeIn  = mkDurField()
      ggFadeIn.value <--> "run:fade-in".attr(0.0)

      val ggFadeOut  = mkDurField()
      ggFadeOut.value <--> "run:fade-out".attr(0.0)

      val ggFreq = DoubleField()
      ggFreq.min       = 0.0
      ggFreq.decimals  = 4
      //ggFreq.max       = 19200.0
      ggFreq.unit      = "Hz"
      ggFreq.value <--> "run:freq".attr(440.0)

      val ggPhase = DoubleField()
      ggPhase.min       = 0.0
      ggPhase.decimals  = 4
      ggPhase.max       = 1.0
      ggPhase.unit      = "    "
      ggPhase.value <--> "run:phase".attr(0.0)

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

      val ggTpe = ComboBox(List(
        "SinOsc", "LFSaw", "Metro", "DC", "WhiteNoise"
      ))
      ggTpe.index <--> "run:signal-type".attr(0)

      ggPhase.enabled = ggTpe.index() < 2
      ggFreq.enabled  = ggTpe.index() < 3

      val p = GridPanel(
        mkLabel("Sound Output:"), out,
        mkLabel("Duration:"), ggDur,
        mkLabel("Amplitude:"), ggGain,
        Label(" "), Label(""),
        mkLabel("Signal Type:"), left(ggTpe),
        mkLabel("Frequency:"), ggFreq,
        mkLabel("Phase (0…1):"), ggPhase,
        mkLabel("Fade in:"), ggFadeIn,
        mkLabel("Fade out:"), ggFadeOut
      )
      p.columns = 2
      p.hGap = 8
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
