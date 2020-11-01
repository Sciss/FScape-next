/*
 *  ModRemoveDC.scala
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

object ModRemoveDC extends Module {
  val name = "Remove DC"

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
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    val f = FScape[T]()
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    import de.sciss.fscape.GE
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 02-Oct-2019
      val in0       = AudioFileIn("in")
      val sr        = in0.sampleRate
      val numFrames = in0.numFrames
      val fileType  = "out-type"  .attr(0)
      val smpFmt    = "out-format".attr(2)
      val gainType  = "gain-type" .attr(1)
      val gainDb    = "gain-db"   .attr(0.0)
      val gainAmt   = gainDb.dbAmp
      val time60ms  = "time-ms"   .attr(30.0)

      val time60    = time60ms * 0.001
      val SR        = 44100.0
      val len60     = (time60 * SR).floor.max(1)
      val floor     = 0.001 // -60.0.dbAmp
      val coef      = (math.log(floor) / len60).exp

      val in        = in0
      // `init` is the overall DC offset in the beginning
      val init      = RunningSum(in).take(len60).last / len60
      // we subtract that so that we don't have the filter
      // "kick in" slowly, but start with minimal elongation
      val off       = BufferMemory(in, len60) - init
      val leak      = off - OnePole(off, coef)
      val sig0: GE  = leak

      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFrames, label)
        ()
      }

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
    import de.sciss.synth.proc.MacroImplicits._
    w.setGraph {
      // version: 02-Oct-2019
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

      val ggTime       = DoubleField()
      ggTime.unit      = "ms (-60 dB point)"
      ggTime.min       = 1.0
      ggTime.max       = 100.0
      ggTime.value <--> "run:time-ms".attr(30.0)

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
        mkLabel("Filter Strength:"), left(ggTime),
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
