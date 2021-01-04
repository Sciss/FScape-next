/*
 *  ModStepAround.scala
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
import de.sciss.proc.Implicits.ObjOps
import de.sciss.proc.{FScape, Widget}

object ModStepAround extends Module {
  val name = "Step Around"

  def any2stringadd: Any = ()

  def apply[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 28-Nov-2020

      val in0         = AudioFileIn("in")
      val numFrames0  = in0.numFrames
      val SR          = in0.sampleRate
      val maxDstLen   = "max-dst-len".attr(60.0 * 4)
      val numFrames   = numFrames0.min((SR * maxDstLen).toLong)
      val log         = "logging".attr(0)
      val logTr       = Metro(0) * log

      def mkIn() = Mix.MonoEqP(AudioFileIn("in"))

      val fftSize     = 2048  // TODO: make available in UI
      val stepDiv     = 2     // TODO: make available in UI
      val sideDur     = "corr-dur".attr(1.0)
      val numMel      = 42
      val numCoef     = 21
      val minFreq     =   100.0
      val maxFreq     = 14000.0
      val startSrcSec = "start-src".attr(0.0).max(0.0)
      val startSrcFr  = (startSrcSec * SR).toInt.min(numFrames)
      val srcLenSec   = "dur-src".attr(10.0).max(0.0)
      val srcFrames0  = (srcLenSec * SR).toInt
      val stopSrcFr   = (startSrcFr + srcFrames0).min(numFrames)
      val srcFrames   = stopSrcFr - startSrcFr
      val destOffSec  = "offset-dst".attr(2.0).max(0.0)
      val destOffFr   = (destOffSec * SR).toLong

      val stepSize: Int = fftSize / stepDiv
      val sideFrames  = (SR * sideDur).toLong
      val sideLen     = (sideFrames / stepSize).toInt.max(1)
      val covSize     = numCoef * sideLen

      val srcLen      = srcFrames / stepSize
      // number of steps through the source
      val srcNumM     = (srcLen - sideLen).max(0)
      val srcNum      = srcNumM + 1

      val inLen       = numFrames / stepSize
      val destOffW    = destOffFr / stepSize
      val startSrcW   = startSrcFr / stepSize
      val lastOffW    = (startSrcW + srcNumM + destOffW).min(inLen)
      val dstLen      = inLen - lastOffW

      val dstNumM     = (dstLen - sideLen).max(0)
      val dstNum      = dstNumM + 1

      val numRuns = srcNum * dstNum

      covSize.poll(logTr, "covSize")
      numRuns.poll(logTr, "numRuns")

      def mkMatrix(in: GE): GE = {
        val lap         = Sliding(in, fftSize, stepSize) * GenWindow.Hann(fftSize)
        val fft         = Real1FFT(lap, fftSize, mode = 2)
        val mag         = fft.complex.mag
        val mel         = MelFilter(mag, fftSize/2, bands = numMel,
          minFreq = minFreq, maxFreq = maxFreq, sampleRate = SR)
        val mfcc        = DCT_II(mel.log.max(-320.0), numMel, numCoef, zero = 0 /* 1 */)
        mfcc
      }

      def mkSpanSigSrc(): GE = {
        val in0         = mkIn()
        val in1         = in0.drop(startSrcW  * stepSize)
        val in          = in1.take(srcLen     * stepSize)
        val mfcc        = mkMatrix(in).take(numCoef * srcNum)
        val slid        = Sliding(mfcc, covSize, numCoef)
        val rp = RepeatWindow(slid, size = covSize, num = dstNum)
        rp
      }

      def mkSpanSigDst(): GE = {
        val in0         = mkIn()
        val dropFr      = (startSrcW + destOffW) * stepSize
        val in1         = in0.drop(dropFr)
        val _dstNum     = dstNum
        val in          = in1.take((_dstNum + srcNumM) * stepSize)
        val mfcc        = mkMatrix(in).take(numCoef * (_dstNum + srcNumM))
        val slid        = Sliding(mfcc, covSize, numCoef)
        val spanStart   = ArithmSeq(0, covSize, srcNum)
        val spanStop    = spanStart + _dstNum * covSize
        val spans       = spanStart zip spanStop
        val slic = Slices(slid, spans)
        slic
      }

      val mfccTotalSize = covSize * numRuns
      val sigSrc = mkSpanSigSrc().take(mfccTotalSize)
      val sigDst = mkSpanSigDst().take(mfccTotalSize)
      // XXX TODO: sometimes 420 (slightly below covSize too short)
      //val sigDst = (mkSpanSigDst() ++ DC(0.0)).take(mfccTotalSize)

      val covIn       = Pearson(sigSrc, sigDst, covSize)
      val keys        = covIn
      val values      = Frames(keys.elastic()) - 1
      val top         = PriorityQueue(keys, values, size = 1)
      val _dstNum     = dstNum    .elastic()  // TODO: do we need elastic?
      val _startSrcW  = startSrcW .elastic()
      val _destOffW   = destOffW  .elastic()
      val srcIdx      = top / _dstNum
      val dstIdx0     = top % _dstNum
      val dstIdx      = dstIdx0 + srcIdx // 'pyramid'
      val topSrcFr    = (_startSrcW + srcIdx) * stepSize
      val topDstFr    = (_startSrcW + _destOffW + dstIdx) * stepSize

      MkLong("res-src", topSrcFr)
      MkLong("res-dst", topDstFr)
    }
    f
  }

  def auxCombine[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 28-Nov-2020

      val inA           = AudioFileIn("in-a")
      val inB           = AudioFileIn("in-b")
      val crossFadeSec  = "cross-fade".attr(1.0)
      val startAFr      = "start-a".attr(0L)
      val startBFr      = "start-b".attr(0L)
      val prepend       = "prepend".attr(0)
      val SR            = inA.sampleRate
      val totalA        = inA.numFrames
      val totalB        = inB.numFrames
      val stopAFr       = "stop-a".attr(Long.MaxValue).min(totalA)
      val stopBFr       = "stop-b".attr(Long.MaxValue).min(totalB)
      val lenAFr        = (stopAFr - startAFr).max(0L)
      val lenBFr        = (stopBFr - startBFr).max(0L)
      val lenMin        = lenAFr min lenBFr
      val fadeFr        = (crossFadeSec * SR).toInt.clip(0, lenMin)

      val inAD          = inA.drop(startAFr)
      val inBD          = inB.drop(startBFr)
      val seg0Fr        = lenAFr - fadeFr
      val seg0          = inAD.take(seg0Fr)
      val inADD         = inAD.drop(seg0Fr)
      val fdOut         = inADD.take(fadeFr)
      val fdIn          = inBD.take(fadeFr)
      val lnFd          = Line(0.0, 1.0, fadeFr)
      val seg1          = fdOut * (1 - lnFd).sqrt + fdIn * lnFd.sqrt
      val seg2Fr        = lenBFr - fadeFr
      val seg2          = inBD.drop(fadeFr).take(seg2Fr)
      val post          = seg0 ++ seg1 ++ seg2

      val cat = If (prepend) Then {
        val pre = AudioFileIn("in-pre")
        pre ++ post
      } Else {
        post
      }

      val numFramesOut = lenAFr + lenBFr - fadeFr // well, plus in-pre...

      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFramesOut, label)
        ()
      }

      val sig = cat

      val written = AudioFileOut("out",
        in            = sig,
        sampleRate    = SR,
        sampleFormat  = 2,
      )

      mkProgress(written, "write")

      MkLong("out-len", Length(written))
    }
    f.name = "Combine"
    f
  }

  def auxFinalize[T <: Txn[T]]()(implicit tx: T): FScape[T] = {
    import de.sciss.fscape.lucre.graph.Ops._
    import de.sciss.fscape.lucre.graph._
    val f = FScape[T]()
    import de.sciss.fscape.lucre.MacroImplicits._
    f.setGraph {
      // version: 03-Apr-2019
      def mkIn() = AudioFileIn("in")

      val in0       = mkIn()
      val sr        = in0.sampleRate
      val numFrames = in0.numFrames
      val gainType  = "gain-type" .attr(1)
      val gainDb    = "gain-db"   .attr(0.0)
      val gainAmt   = gainDb.dbAmp
      val fileType  = "out-type"  .attr(0)
      val smpFmt    = "out-format".attr(2)

      def mkProgress(x: GE, label: String): Unit = {
        ProgressFrames(x, numFrames, label)
        ()
      }

      val mul       = If (gainType sig_== 0) Then {
        val in1       = mkIn()
        val rMax      = RunningMax(Reduce.max(in1.abs))
        mkProgress(rMax, "analyze")
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
    f.name = "finalize"
    f
  }

  def ui[T <: Txn[T]]()(implicit tx: T): Widget[T] = {
    import de.sciss.proc.ExImport._
    import de.sciss.lucre.expr.graph._
    import de.sciss.lucre.swing.graph._
    val w = Widget[T]()
    import de.sciss.proc.MacroImplicits._
    w.setGraph {
      // version 28-Nov-2020

      val DEBUG_LOG = false

      val rMatch    = Runner("run"          )
      val rWriteDb  = Runner("run-combine"  )
      val rWritePh  = Runner("run-combine"  )
      val rFinalize = Runner("run-finalize" )

      val runners = Seq(
        (rMatch   , "Match"         ),
        (rWriteDb , "Write Database"),
        (rWritePh , "Write Phrase"  ),
        (rFinalize, "Finalize"      ),
      )

      def mkSecField(key: String, default: Double, min: Double = 0.01) = {
        val gg = DoubleField()
        gg.unit = "s"
        gg.min  = min
        gg.max  = 600.0
        gg.decimals = 3
        gg.step = 0.01
        gg.value <--> key.attr(default)
        gg
      }

      val ggCorrDur   = mkSecField("corr-dur"   , default =   1.0 )
      val ggSrcMaxDur = mkSecField("dur-src"    , default =  10.0 )
      val ggSrcStart  = mkSecField("start-src"  , default =   0.01)
      val ggDstOff    = mkSecField("offset-dst" , default =   2.0 )
      val ggMaxDstLen = mkSecField("max-dst-len", default = 240.0, min = 1.0)
      val ggFadeDur   = mkSecField("fade-dur"   , default =   0.3 )

      val in    = AudioFileIn()
      in.value <--> Artifact("in")

      val out   = AudioFileOut()
      out.value         <--> Artifact("out")
      out.fileType      <--> "out-type".attr(0)
      out.sampleFormat  <--> "out-format".attr(2)

      val ggGain = DoubleField()
      ggGain.unit = "dB"
      ggGain.min  = -180.0
      ggGain.max  = +180.0
      ggGain.value <--> "gain-db".attr(-0.2)

      val ggGainType = ComboBox(
        List("Normalized", "Immediate")
      )
      ggGainType.index <--> "gain-type".attr(0)

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

      val pIO = GridPanel(
        mkLabel("Input:" ), in,
        mkLabel("Output:"), out,
        mkLabel("Gain:"), left(ggGain, ggGainType),
      )
      val pPar = GridPanel(
        mkLabel("Min. Fragment Length:"           ), left(ggSrcStart),
        mkLabel("Correlation Length:"             ), left(ggCorrDur),
        mkLabel("Max. Source Search Length:"      ), left(ggSrcMaxDur),
        mkLabel("Max. Destination Search Length:" ), left(ggMaxDstLen),
        mkLabel("Min. Jump Length:"               ), left(ggDstOff),
        mkLabel("Cross-Fade Length:"              ), left(ggFadeDur),
      )
      pIO .columns = 2
      pIO .hGap    = 8
      pIO .compact = true
      pPar.columns = 2
      pPar.hGap    = 8
      pPar.compact = true

      val p = GridPanel(pIO, pPar)
      p.vGap        = 8
      p.columns     = 1
      p.compactRows = true

      val resMatchSrc = Var(0L)
      val resMatchDst = Var(0L)

      val specIn      = AudioFileSpec.read(in.value()).getOrElse(AudioFileSpec.Empty())
      val inFrames    = specIn.numFrames
      val SR          = specIn.sampleRate

      val tmpDir      = File.TmpDir()
      val ts          = TimeStamp()
      val outDb1      = tmpDir / "step-db1-%d.aif".format(ts)
      val outDb2      = tmpDir / "step-db2-%d.aif".format(ts)
      val outPh1      = tmpDir / "step-ph1-%d.aif".format(ts)
      val outPh2      = tmpDir / "step-ph2-%d.aif".format(ts)
      val tmpFiles    = Seq(outDb1, outDb2, outPh1, outPh2)

      val resDbLen      = Var(0L)
      val resPhLen      = Var(0L)
      val renderEnabled = Var(true)
      val dbInFile      = Var(in.value())
      val dbOutFile     = Var(outDb1)   // there should be Artifact.Empty()...
      val phInFile      = Var(outPh2)
      val phOutFile     = Var(outPh1)
      val iter          = Var(0)  // zero-based

      val fadeDur         = ggFadeDur   .value() //  0.3
      val corrDur         = ggCorrDur   .value() //  1.0
      val startMatchSec   = ggSrcStart  .value() //  0.5
      val lenMatchSrcSec  = ggSrcMaxDur .value() // 10.0
      val offsetMatchSec  = ggDstOff    .value() //  2.0

      val minDbDur      = startMatchSec + lenMatchSrcSec + offsetMatchSec + corrDur
      val minDbDurFr    = (SR * minDbDur).toLong

      val fadeFr        = (SR * fadeDur).toInt
      val corrFr        = (SR * corrDur).toInt
      val corrFrH       = corrFr/2
      val fadeFrH       = fadeFr/2

      val actMatch = Act(
        if (DEBUG_LOG) PrintLn("Starting match. len = " ++ resDbLen.toStr) else Act.Nop(),
        //  Artifact("run-match:in").set(dbInFile),
        //  "run-match:start-src" .attr[Double]   .set(startMatchSec),
        //  "run-match:dur-src"   .attr[Double]   .set(lenMatchSrcSec),
        //  "run-match:offset-dst".attr[Double]   .set(offsetMatchSec),
        //  "run-match:corr-dur"  .attr[Double]   .set(corrDur),
        rMatch.runWith(
          "in"          -> dbInFile,
          "start-src"   -> startMatchSec,
          "dur-src"     -> lenMatchSrcSec,
          "offset-dst"  -> offsetMatchSec,
          "corr-dur"    -> corrDur,
          "res-src"     -> resMatchSrc,
          "res-dst"     -> resMatchDst,
          "max-dst-len" -> ggMaxDstLen.value(),
          "corr-dur"    -> ggCorrDur  .value(),
          "start-src"   -> ggSrcStart .value(),
          "dur-src"     -> ggSrcMaxDur.value(),
          "offset-dst"  -> ggDstOff   .value(),
          "logging"     -> DEBUG_LOG,
        )
      )

      val vrProgress = Var(0)

      val actStart = Act(
        renderEnabled .set(false),
        iter          .set(0),
        vrProgress    .set(0),
        ts.update,
        resDbLen      .set(inFrames),
        resPhLen      .set(0L),
        dbInFile      .set(in.value()),
        dbOutFile     .set(outDb1),
        phInFile      .set(in.value()), // unused in first iteration, but opened
        phOutFile     .set(outPh1),
        actMatch,
      )

      val actFinalize = Act(
        rFinalize.runWith(
          "in"          -> phOutFile,
          "out"         -> out.value(),
          "out-type"    -> out.fileType(),
          "out-format"  -> out.sampleFormat(),
          "gain-db"     -> ggGain.value(),
          "gain-type"   -> ggGainType.index(),
        )
      )

      val actRenderNext = Act(
        vrProgress.set((resPhLen * 1000 / inFrames).toInt),
        If (resDbLen >= minDbDurFr) Then {
          Act(
            iter.set(iter + 1),
            if (DEBUG_LOG) PrintLn("Next iteration " ++ (iter + 1).toStr) else Act.Nop(),
            If ((iter % 2) sig_== 1) Then {
              Act(  // second (1), fourth (3), ... iteration
                dbInFile      .set(outDb1),
                dbOutFile     .set(outDb2),
                phInFile      .set(outPh1),
                phOutFile     .set(outPh2),
              )
            } Else {
              Act(  // third (2), fifth (4), ... iteration
                dbInFile      .set(outDb2),
                dbOutFile     .set(outDb1),
                phInFile      .set(outPh2),
                phOutFile     .set(outPh1),
              )
            },
            actMatch,
          )
        } Else {
          Act(
            if (DEBUG_LOG) PrintLn("DB exhausted.") else Act.Nop(),
            actFinalize,
          )
        }
      )

      val startPhA  = 0L
      val stopPhA   = resMatchSrc + corrFrH + fadeFrH
      val startPhB  = resMatchDst + corrFrH - fadeFrH
      //val offPhB    = stopPhA  - fadeFr
      val stopPhB   = startPhB + fadeFr

      val startDbA  = stopPhB
      val stopDbA   = Long.MaxValue
      val startDbB  = stopPhA - fadeFr
      //val offDbB    = ...
      val stopDbB   = startPhB

      rMatch.done ---> Act(
        if (DEBUG_LOG) PrintLn("Match done: src = " ++
          resMatchSrc.toStr ++ ", dst = " ++ resMatchDst.toStr) else Act.Nop(),
        //  Artifact("run-combine:in-a").set(dbInFile),
        //  Artifact("run-combine:in-b").set(dbInFile),
        //  "run-combine:start-a" .attr[Long].set(startDbA),
        //  "run-combine:stop-a"  .attr[Long].set(stopDbA),
        //  "run-combine:start-b" .attr[Long].set(startDbB),
        //  "run-combine:stop-b"  .attr[Long].set(stopDbB),
        //  "run-combine:prepend" .attr[Boolean].set(false),
        //  Artifact("run-combine:in-pre").set(phInFile),  // unused, but must be given
        //  "run-combine:cross-fade".attr[Double].set(fadeDur),
        //  Artifact("run-combine:out").set(dbOutFile),
        rWriteDb.runWith(
          "in-a"        -> dbInFile,
          "in-b"        -> dbInFile,
          "start-a"     -> startDbA,
          "stop-a"      -> stopDbA,
          "start-b"     -> startDbB,
          "stop-b"      -> stopDbB,
          "prepend"     -> false,
          "in-pre"      -> phInFile,  // unused, but must be given
          "cross-fade"  -> fadeDur,
          "out"         -> dbOutFile,
          "out-len"     -> resDbLen,
          "logging"     -> DEBUG_LOG,
        ),
      )

      val actWriteDbDone = Act(
        //  PrintLn("DB updated. len = " ++ resDbLen.toStr),
        //  PrintLn("startPhA 0, stopPhA " ++ stopPhA.toStr ++
        //    ", startPhB " ++ startPhB.toStr ++ ", stopPhB " ++ stopPhB.toStr),
        rWritePh.runWith(
          "in-a"        -> dbInFile,
          "in-b"        -> dbInFile,
          "start-a"     -> startPhA,
          "stop-a"      -> stopPhA,
          "start-b"     -> startPhB,
          "stop-b"      -> stopPhB,
          "prepend"     -> (iter > 0),
          "in-pre"      -> phInFile,  // unused, but must be given
          "cross-fade"  -> fadeDur,
          "out"         -> phOutFile,
          "out-len"     -> resPhLen,
          "logging"     -> DEBUG_LOG,
        ),
      )

      rWriteDb.done ---> actWriteDbDone
      //rWriteDb.done ---> Act(
      //  PrintLn("Debug stop"),
      //  renderEnabled.set(true),
      //)

      rWritePh.done ---> Act(
        if (DEBUG_LOG) PrintLn("Output written. len = " ++ resPhLen.toStr) else Act.Nop(),
        actRenderNext,
      )

      val actCleanup = {
        val deleteTmp = tmpFiles.map(_.delete)
        Act(deleteTmp :+ renderEnabled.set(true): _*)
      }

      val actStop = {
        val allStop = runners.map { case (r, _) => r.stop }
        Act(allStop :+ actCleanup: _*)
      }

      runners.foreach { case (r, name) =>
        r.failed ---> Act(
          PrintLn(name ++ " failed:\n" ++ r.messages.mkString("\n")),
          actCleanup,
        )
      }

      rFinalize.done ---> Act(
        if (DEBUG_LOG) PrintLn("All done.") else Act.Nop(),
        vrProgress.set(1000),
        actCleanup,
      )

      val ggRender  = Button(" Render ")
      val ggCancel  = Button(" X ")
      ggCancel.tooltip = "Cancel Rendering"
      val pb        = ProgressBar()
      ggRender.clicked ---> actStart
      ggCancel.clicked ---> actStop

      ggRender.enabled = renderEnabled
      ggCancel.enabled = !renderEnabled
      //pb.value = (rMatch.progress * 100).toInt
      pb.max    = 1000
      pb.value  = vrProgress

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
    w.attr.put("run-combine"  , auxCombine  ())
    w.attr.put("run-finalize" , auxFinalize ())
    w
  }
}
