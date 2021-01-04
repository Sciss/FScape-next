/*
 *  ModCheckChannelBalance.scala
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

import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
import de.sciss.lucre.Txn
import de.sciss.proc.{FScape, Widget}

object ModCheckChannelBalance extends Module {
  val name = "Check Channel Balance"

  def any2stringadd: Any = ()

  /**
    * Input Attributes:
    *
    * - `"in"`: audio file input
    * - `"freq"`: high pass filter frequency for the RMS calculation, or zero to disable filter
    *
    * Output Attributes:
    *
    * - `"out-peak"`: a vector of double numbers, reflecting the peak amplitude in dBFS per channel
    * - `"out-rms"`: a vector of double numbers, reflecting the RMS energy in dBFS per channel
    */
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 04-Oct-2020
      val in      = AudioFileIn("in")
      val SR      = in.sampleRate
      val freq    = "freq".attr(0)
      val inSq    = If (freq > 0) Then {
        HPF(in, freq / SR).squared
      } Else {
        in.squared
      }
      val sumR    = RunningSum(inSq)
      ProgressFrames(sumR.out(0), in.numFrames)
      val sum     = sumR.last
      val peak    = RunningMax(in.abs).last
      val peakDb  = peak.ampDb
      val rms     = (sum / in.numFrames).sqrt
      val rmsDb   = rms.ampDb
      val peakZip = ZipWindowN(peakDb)
      val rmsZip  = ZipWindowN(rmsDb)
      MkDoubleVector("out-peak", peakZip)
      MkDoubleVector("out-rms", rmsZip)
      ()
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
      // version: 04-Oct-2020
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in    = AudioFileIn()
      in.value <--> Artifact("run:in")

      val resPeak     = Var(Seq.empty[Double])
      val resRMS      = Var(Seq.empty[Double])
      val resTxtPeak  = Var("")
      val resTxtRMS   = Var("")
      val outPeak     = Label(resTxtPeak)
      val outRMS      = Label(resTxtRMS)

      val ggHPF       = DoubleField()
      ggHPF.unit      = "Hz"
      ggHPF.min       = 0.0
      ggHPF.max       = 1000.0
      ggHPF.value <--> "hpf".attr(0.0)

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
        mkLabel("Peak [dB]:"), outPeak,
        mkLabel("RMS [dB]:"), outRMS,
        mkLabel("RMS HPF [Hz]:"), left(ggHPF),
      )
      p.columns = 2
      p.hGap    = 8
      p.compact = true

      val ggRender  = Button(" Analyze ")
      val ggCancel  = Button(" X ")
      ggCancel.tooltip = "Cancel Analysis"
      val pb        = ProgressBar()

      r.failed ---> Act(
        resTxtPeak.set(r.messages.mkString("Failed: ", ",", ""))
      )

      r.done ---> Act(
        resTxtPeak.set(resPeak.map(d => "%1.1f".format(d)).mkString(", ")),
        resTxtRMS .set(resRMS .map(d => "%1.1f".format(d)).mkString(", ")),
      )

      ggRender.clicked ---> Act(
        resTxtPeak.set(""),
        resTxtRMS .set(""),
        r.runWith(
          "out-peak"  -> resPeak,
          "out-rms"   -> resRMS,
          "freq"      -> ggHPF.value(),
        ),
      )
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
