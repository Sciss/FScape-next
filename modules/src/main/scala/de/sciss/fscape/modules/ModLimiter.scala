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
import de.sciss.lucre.Txn
import de.sciss.synth.proc.{FScape, Widget}

import scala.Predef.{any2stringadd => _}

object ModLimiter extends Module {
  val name = "Limiter"

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
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 03-Apr-2019
      val in0       = AudioFileIn("in")
      val sr        = in0.sampleRate
      val numFrames = in0.numFrames
      val fileType  = "out-type"  .attr(0)
      val smpFmt    = "out-format".attr(2)
      val boostDb   = "gain-db"   .attr(3.0)
      val ceilDb    = "ceil-db"   .attr(-0.2)
      val atkMs     = "atk-ms"    .attr(20.0)
      val rlsMs     = "rls-ms"    .attr(200.0)
      val syncChans = "sync-chans".attr(1)
      val boostAmt  = boostDb.dbAmp
      val ceilAmt   = ceilDb.dbAmp
      val atkFrames = (atkMs/1000) * sr
      val rlsFrames = (rlsMs/1000) * sr

      val in    = in0 * boostAmt
      val gain0 = Limiter(in, attack = atkFrames, release = rlsFrames,
        ceiling = ceilAmt)
      val inBuf = BufferMemory(in, atkFrames + rlsFrames)
      val gain  = If (syncChans sig_== 0) Then { gain0 } Else {
        Reduce.min(gain0)
      }
      val sig   = inBuf * gain
      val written = AudioFileOut("out", sig, fileType = fileType,
        sampleFormat = smpFmt, sampleRate = sr)
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
      // version: 02-Apr-2019
      val r     = Runner("run")
      val m     = r.messages
      m.changed.filter(m.nonEmpty) ---> PrintLn(m.mkString("\n"))

      val in    = AudioFileIn()
      in.value <--> Artifact("run:in")
      val out   = AudioFileOut()
      out.value         <--> Artifact("run:out")
      out.fileType      <--> "run:out-type".attr(0)
      out.sampleFormat  <--> "run:out-format".attr(2)

      val ggBoost = DoubleField()
      ggBoost.unit = "dB"
      ggBoost.min  = -180.0
      ggBoost.max  = +180.0
      ggBoost.value <--> "run:gain-db".attr(3.0)

      val ggCeil = DoubleField()
      ggCeil.unit = "dB"
      ggCeil.min  = -180.0
      ggCeil.max  = +180.0
      ggCeil.value <--> "run:ceil-db".attr(-0.2)

      val ggAtk = DoubleField()
      ggAtk.unit = "ms"
      ggAtk.min  = 0.0
      ggAtk.max  = 60 * 60 * 1000.0
      ggAtk.value <--> "run:atk-ms".attr(20.0)

      val ggRls = DoubleField()
      ggRls.unit = "ms"
      ggRls.min  = 0.0
      ggRls.max  = 60 * 60 * 1000.0
      ggRls.value <--> "run:rls-ms".attr(200.0)

      val ggSync = CheckBox("Synchronize Channels")
      ggSync.selected <--> "run:sync-chans".attr(true)

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
        Label(" "), Label(" "),
        mkLabel("Boost:"  ), left(ggBoost, mkLabel("    Attack [-60 dB]:" ), ggAtk),
        mkLabel("Ceiling:"), left(ggCeil , mkLabel("  Release [-60 dB]:"), ggRls),
        mkLabel(" "), ggSync
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
