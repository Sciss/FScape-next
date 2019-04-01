/*
 *  ModChangeGain.scala
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

package de.sciss.fscape.modules

import de.sciss.fscape.GE
import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Widget

import scala.Predef.{any2stringadd => _}

object ModChangeGain extends Module {
  val name = "Change Gain"

  /**
    * Attributes:
    *
    * - `"in"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-type"`: either `0` (normalized) or `1` (relative)
    * - `"gain-db"`: gain factor with respect to gain-type (headroom or factor), in decibels
    */
  def apply[S <: Sys[S]]()(implicit tx: S#Tx): FScape[S] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[S]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 28-Mar-2019
      def mkIn() = AudioFileIn("in")

      val in0       = mkIn()
      val sr        = in0.sampleRate
      val numFrames = in0.numFrames
      val gainType  = "gain-type" .attr(1)
      val gainDb    = "gain-db"   .attr(0.0)
      val gainAmt   = gainDb.dbAmp
      val fileType  = "out-type"  .attr(0)
      val smpFmt    = "out-format".attr(2)

      def mkProgress(frames: GE, label: String): Unit =
        Progress(frames / numFrames, Metro(sr) | Metro(numFrames - 1),
          label)

      val mul       = If (gainType sig_== 0) Then {
        val in1       = mkIn()
        val rMax      = RunningMax(Reduce.max(in1.abs))
        val read      = Frames(rMax)
        mkProgress(read, "analyze")
        val maxAmp    = rMax.last
        val div       = maxAmp + (maxAmp sig_== 0.0)
        gainAmt / div
      } Else {
        gainAmt
      }
      val sig = in0 * mul
      val written = AudioFileOut("out", sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sr)
      mkProgress(written, "write")
    }
    f
  }

  def ui[S <: Sys[S]]()(implicit tx: S#Tx): Widget[S] = {
    import de.sciss.lucre.expr.graph._
    import de.sciss.lucre.expr.ExOps._
    import de.sciss.lucre.swing.graph._
    val w = Widget[S]()
    import de.sciss.synth.proc.MacroImplicits._
    w.setGraph {
      // version: 31-Mar-2019
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> Println(m.mkString("\n"))

      val in    = AudioFileIn()
      in.value <--> Artifact("run:in")
      val out   = PathField()
      out.mode  = PathField.Save
      out.value <--> Artifact("run:out")

      val ggOutType = ComboBox(
        List("AIFF", "Wave", "Wave64", "IRCAM", "Snd")
      )
      ggOutType.index <--> "run:out-type".attr(0)

      val ggOutFmt = ComboBox(List(
        "16-bit int",
        "24-bit int",
        "32-bit float",
        "32-bit int",
        "64-bit float"
      ))
      ggOutFmt.index <--> "run:out-format".attr(2)

      val ggGain = DoubleField()
      ggGain.unit = "dB"
      ggGain.min  = -180.0
      ggGain.max  = +180.0
      ggGain.value <--> "run:gain-db".attr(0.0)

      val ggGainType = ComboBox(
        List("Normalized", "Immediate")
      )
      ggGainType.index <--> "run:gain-type".attr(1)

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
        Label(" "), left(ggOutType, ggOutFmt),
        Label(" "), Empty(),
        mkLabel("Gain:"), left(ggGain, ggGainType),
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
      val stopped = r.state sig_== 0
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
