package de.sciss.fscape.modules

import de.sciss.fscape.GE
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Widget

import scala.Predef.{any2stringadd => _}

object MakeLoopModule extends Module {
  val name = "Make Loop"

  def apply[S <: Sys[S]]()(implicit tx: S#Tx): FScape[S] = {
    import de.sciss.fscape.graph.{AudioFileIn => _, AudioFileOut => _, _}
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[S]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 30-Mar-2019
      val numFramesIn   = AudioFileIn.NumFrames ("in")
      val sr            = AudioFileIn.SampleRate("in")
      val fileType      = "out-type"      .attr(0)
      val smpFmt        = "out-format"    .attr(2)
      val gainType      = "gain-type"     .attr(1)
      val gainDb        = "gain-db"       .attr(0.0)
      val fadeLenMs     = "fade-len-ms"   .attr(1000.0).max(0.0)
      val initSkipMs    = "init-skip-ms"  .attr(1000.0).max(0.0)
      val finalSkipMs   = "final-skip-ms" .attr(   0.0).max(0.0)
      // fadePos: 0 pre, 1 = post
      val fadePos       = "fade-pos"      .attr(0).clip(0, 1)
      val fadeType      = "fade-type"     .attr(1).clip(0, 1)
      val gainAmt       = gainDb.dbAmp
      val fadeLen0      = ((fadeLenMs  /1000) * sr).roundTo(1)
      val initSkip0     = ((initSkipMs /1000) * sr).roundTo(1)
      val finalSkip0    = ((finalSkipMs/1000) * sr).roundTo(1)
      val initSkip      = initSkip0   min numFramesIn
      val finalSkip     = finalSkip0  min (numFramesIn - initSkip)
      val fadeAvail     = (initSkip  * (fadePos sig_== 0)).max(
                           finalSkip * (fadePos sig_== 1))
      val fadeLen       = fadeLen0    min fadeAvail
      val numFramesOut  = numFramesIn  - (initSkip + finalSkip)
      val steadyLen     = numFramesOut - fadeLen
      val shapeId       = fadeType * 3 + (1: GE) // 1 = linear, 4 = welch

      def mkIn() = AudioFileIn("in")

      val faded = If (fadePos sig_== 0) Then {
        val c1  = mkIn().drop(initSkip).take(numFramesOut)
        val c1F = c1 * DEnvGen(
          levels  = (1.0: GE) ++ (1.0: GE) ++ (0.0: GE),
          lengths = steadyLen ++ fadeLen,
          shapes  = (0: GE) ++ shapeId
        )
        val c2 = mkIn().drop(initSkip - fadeLen).take(fadeLen)
        val c2F = c2 * DEnvGen(
          levels  = (0.0: GE) ++ (1.0: GE),
          lengths = fadeLen,
          shapes  = shapeId
        )
        val c2FC = DC(0.0).take(steadyLen) ++ c2F
        c1F + c2FC

      } Else {
        val c1  = mkIn().drop(initSkip).take(numFramesOut)
        val c1F = c1 * DEnvGen(
          levels  = (0.0: GE) ++ (1.0: GE) ++ (1.0: GE),
          lengths = fadeLen ++ steadyLen,
          shapes  = shapeId ++ (0: GE)
        )
        val c2 = mkIn().drop(initSkip + numFramesOut).take(fadeLen)
        val c2F = c2 * DEnvGen(
          levels  = (1.0: GE) ++ (0.0: GE),
          lengths = fadeLen,
          shapes  = shapeId
        )
        val c2FC = c2F ++ DC(0.0).take(steadyLen)
        c1F + c2FC
      }

      def mkProgress(frames: GE, label: String): Unit =
        Progress(frames / numFramesOut,
          Metro(sr) | Metro(numFramesOut - 1),
          label)

      val sig = If (gainType sig_== 0) Then {
        val rsmpBuf   = BufferDisk(faded)
        val rMax      = RunningMax(Reduce.max(faded.abs))
        val read      = Frames(rMax)
        mkProgress(read, "analyze")
        val maxAmp    = rMax.last
        val div       = maxAmp + (maxAmp sig_== 0.0)
        val gainAmtN  = gainAmt / div
        rsmpBuf * gainAmtN

      } Else {
        faded * gainAmt
      }

      val written = AudioFileOut("out", sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sr)
      mkProgress(written, "write")
    }
    f
  }

  def ui[S <: Sys[S]]()(implicit tx: S#Tx): Widget[S] = {
    import de.sciss.lucre.expr.ExOps._
    import de.sciss.lucre.swing.graph._
    val w = Widget[S]()
    import de.sciss.synth.proc.MacroImplicits._
    w.setGraph {
      // version: 30-Mar-2019
      val r     = Runner("run")
      val in    = PathField()
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

      val ggFadeLen     = DoubleField()
      ggFadeLen.unit    = "ms"
      ggFadeLen.min     = 0.0
      ggFadeLen.max     = 60 * 60 * 1000.0
      ggFadeLen.value <--> "run:fade-len-ms".attr(1000.0)

      val ggInitSkip    = DoubleField()
      ggInitSkip.unit   = "ms"
      ggInitSkip.min    = 0.0
      ggInitSkip.max    = 60 * 60 * 1000.0
      ggInitSkip.value <--> "run:init-skip-ms".attr(1000.0)

      val ggFinalSkip   = DoubleField()
      ggFinalSkip.unit  = "ms"
      ggFinalSkip.min   = 0.0
      ggFinalSkip.max   = 60 * 60 * 1000.0
      ggFinalSkip.value <--> "run:final-skip-ms".attr(0.0)

      val ggFadePos = ComboBox(List(
        "End of Loop \\ Pre Loop /",
        "Begin of Loop / Post Loop \\",
      ))
      ggFadePos.index <--> "run:fade-pos".attr(0)

      val ggFadeType = ComboBox(List(
        "Equal Energy",
        "Equal Power",
      ))
      ggFadeType.index <--> "run:fade-type".attr(1)

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
        mkLabel("Input:" ), left(in),
        mkLabel("Output:"), left(out),
        Label(" "), left(ggOutType, ggOutFmt),
        mkLabel("Gain:"), left(ggGain, ggGainType),
        Label(" "), Empty(),
        mkLabel("Fade Length:"), left(ggFadeLen,
          Label("  Fade Position:"), ggFadePos),
        mkLabel("Initial Skip:"), left(ggInitSkip,
          mkLabel("       Fade Type:"), ggFadeType),
        mkLabel("Final Skip:"), left(ggFinalSkip),
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
