/*
 *  ModBleach.scala
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

import scala.Predef.{any2stringadd => _}

// XXX TODO should flip order in two-way order (first backward then forward) to match FScape-classic behaviour
object ModBleach extends Module {
  val name = "Bleach"

  /**
    * Attributes:
    *
    * - `"in"`: audio file input
    * - `"out"`: audio file output
    * - `"out-type"`: audio file output type (AIFF: 0, Wave: 1, Wave64: 2, IRCAM: 3, NeXT: 4)
    * - `"out-format"`: audio file output sample format (Int16: 0, Int24: 1, Float: 2, Int32: 3, Double: 4, UInt8: 5, Int8: 6)
    * - `"gain-db"`: input boost factor (before entering limiter), in decibels
    * - `"filter-len"`: filter length in frames
    * - `"feedback-db"`: feedback gain in decibels (typically -50 or -60)
    * - `"clip-db"`: maximum filter gain in decibels
    * - `"two-ways"`: whether to run the process two times, once forward and once backward
    */
  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    val f = FScape[T]()
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    import de.sciss.fscape.GE
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 05-Apr-2020
      def mkIn() = AudioFileIn("in")

      val in            = mkIn()
      val sr            = in.sampleRate
      val numFrames     = in.numFrames

      val fileType      = "out-type"      .attr(0)
      val smpFmt        = "out-format"    .attr(2)
      val gainType      = "gain-type"     .attr(1)
      val gainDb        = "gain-db"       .attr(0.0)
      val filterLen     = "filter-len"    .attr(400)
      val feedbackDb    = "feedback-db"   .attr(-60.0)
      val filterClipDb  = "clip-db"       .attr( 18.0)
      val twoWays       = "two-ways"      .attr(0)
      val colorize      = "colorize"      .attr(0)
      //val sideChain     = "ana-is-filter" .attr(1)

      val feedback      = feedbackDb.dbAmp
      val filterClip    = filterClipDb.dbAmp
      val gainAmt       = gainDb.dbAmp

      def bleach(x: GE): GE = {
        val b = Bleach(x: GE,
          filterLen   = filterLen,
          feedback    = feedback,
          filterClip  = filterClip
        )
        If (colorize) Then {
          b
        } Else {
          x - b
        }
      }

      def reverse(x: GE): GE =
        Slices(x, numFrames :+ 0L)  // reverse trick

      val sig0 = bleach(in)

      // XXX TODO -- we need to solve the issue of multiple progress components
      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFrames, label)
        ()
      }

      val sig1 = sig0

      val sig2 = If (twoWays) Then {
        mkProgress(sig1, "pass 1")
        val rvs1 = reverse(sig1)
        val flt2 = bleach(rvs1)
        reverse(flt2)
      } Else {
        sig1
      }

      val sigDone = sig2

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
          mkProgress(x, "analyze")
          x * gainAmt
        }

      val sig = applyGain(sigDone)

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
      // version: 05-Apr-2020
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in = AudioFileIn()
      in.value <--> Artifact("run:in")
      val out   = AudioFileOut()
      out.value         <--> Artifact("run:out")
      out.fileType      <--> "run:out-type".attr(0)
      out.sampleFormat  <--> "run:out-format".attr(2)

      //val inFlt = AudioFileIn()
      //inFlt.value <--> Artifact("run:in-flt")

      //val ggAnaIsFlt = CheckBox("Use Analysis File")
      //ggAnaIsFlt.selected <--> "run:ana-is-filter".attr(true)

      val ggGain = DoubleField()
      ggGain.unit = "dB"
      ggGain.min  = -180.0
      ggGain.max  = +180.0
      ggGain.value <--> "run:gain-db".attr(0.0)

      val ggGainType = ComboBox(
        List("Normalized", "Immediate")
      )
      ggGainType.index <--> "run:gain-type".attr(1)

      val ggFltLen = IntField()
      ggFltLen.unit      = "frames"
      ggFltLen.min       = 1
      ggFltLen.value <--> "run:filter-len".attr(400)

      val ggFeedback = DoubleField()
      ggFeedback.unit      = "dB"
      ggFeedback.min       = -320.0
      ggFeedback.max       =    0.0
      ggFeedback.decimals  = 1
      ggFeedback.value <--> "run:feedback-db".attr(-60.0)

      val ggClip = DoubleField()
      ggClip.unit      = "dB"
      ggClip.min       =   0.0
      ggClip.max       = 320.0
      ggClip.decimals  = 1
      ggClip.value <--> "run:clip-db".attr(18.0)

      val ggTwoWays = CheckBox("Two Ways (Backward-Forward)")
      ggTwoWays.selected <--> "run:two-ways".attr(false)

      val ggColorize = CheckBox("Inverse Operation (Colorize)")
      ggColorize.selected <--> "run:colorize".attr(false)

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
        //  mkLabel("Filter Input:"), ggAnaIsFlt,
        mkLabel("Output:"), out,
        mkLabel("Gain:"), left(ggGain, ggGainType),
        Label(" "), Empty(),
        mkLabel("Filter Length:"), left(ggFltLen),
        mkLabel("Feedback Gain:"), left(ggFeedback),
        mkLabel("Filter Clip:"  ), left(ggClip),
        Empty(), ggTwoWays,
        Empty(), ggColorize,
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
