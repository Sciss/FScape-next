/*
 *  ModLimiter.scala
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
import de.sciss.lucre.Txn
import de.sciss.synth.proc.Widget

import scala.Predef.{any2stringadd => _}

object ModSlewRateLimiter extends Module {
  val name = "Slew Rate Limiter"

  /**
    * Attributes:
    *
    * - `"in"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-db"`: input boost factor (before entering limiter), in decibels
    * - `"ceil-db"`: limiter clipping level, in decibels
    * - `"limit"`: limiting slew rate amplitude
    * - `"leak-dc"`: whether to remove DC offset (1) or not (0)
    */
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.GE
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 24-Jun-2020

      val in0         = AudioFileIn("in")
      val sampleRate  = in0.sampleRate
      val framesIn    = in0.numFrames
      val framesOut   = framesIn

      val gainType    = "gain-type" .attr(0)
      val gainDb      = "gain-db"   .attr(0.0)
      val gainAmt     = gainDb.dbAmp
      val fileType    = "out-type"  .attr(0)
      val smpFmt      = "out-format".attr(1)
      val limVal      = "limit"     .attr(0.1)
      val leakDC      = "leak-dc"   .attr(1)

      def mkProgress(x: GE, n: GE, label: String): Unit = {
        ProgressFrames(x, n, label)
        ()
      }

      val dif   = Differentiate(in0)
      val lim   = dif.clip2(limVal)
      val int   = RunningSum(lim)
      val sig0  = If (leakDC sig_== 0) Then int Else LeakDC(int)

      val sig = If (gainType sig_== 0) Then {
        val sig0Buf   = BufferDisk(sig0)
        val rMax      = RunningMax(Reduce.max(sig0.abs))
        val read      = Frames(rMax)
        mkProgress(read, framesOut, "analyze")
        val maxAmp    = rMax.last
        val div       = maxAmp + (maxAmp sig_== 0.0)
        val gainAmtN  = gainAmt / div
        sig0Buf * gainAmtN

      } Else {
        sig0 * gainAmt
      }

      val written     = AudioFileOut("out", sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sampleRate)
      mkProgress(written, framesOut, "written")
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
      // version: 24-Jun-2020
      val r = Runner("run")
      val m = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in = AudioFileIn()
      in.value <--> Artifact("run:in")

      val out = AudioFileOut()
      out.value <--> Artifact("run:out")
      out.fileType <--> "run:out-type".attr(0)
      out.sampleFormat <--> "run:out-format".attr(1)

      val ggGain = DoubleField()
      ggGain.unit = "dB"
      ggGain.min  = -180.0
      ggGain.max  = +180.0
      ggGain.value <--> "run:gain-db".attr(0.0)

      val ggGainType = ComboBox(
        List("Normalized", "Immediate")
      )
      ggGainType.index <--> "run:gain-type".attr(0)

      val ggLimVal = DoubleField()
      ggLimVal.min  = 0.001
      ggLimVal.max  = 1.0
      ggLimVal.decimals  = 3
      ggLimVal.value <--> "run:limit".attr(0.1)

      val ggLeakDC = CheckBox()
      ggLeakDC.selected <--> "run:leak-dc".attr(true)

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
        mkLabel("Sound Input:" ), in,
        mkLabel("Input Output:"), out,
        mkLabel("Gain:"), left(ggGain, ggGainType),
        Label(" "), Label(""),
        mkLabel("Limit:"), left(ggLimVal),
        mkLabel("Remove DC:"), left(ggLeakDC)
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
      val stopped = (r.state sig_== 0) || (r.state sig_== 4)
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
