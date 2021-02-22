/*
 *  PhysicalOut.scala
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

package de.sciss.fscape
package lucre.stream

import akka.stream.{Attributes, Inlet}
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.Handlers.InDMain
import de.sciss.fscape.stream.impl.shapes.UniformSinkShape
import de.sciss.fscape.stream.impl.{Handlers, NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufD, Builder, Control, InD, Layer, OutD, OutI}
import de.sciss.lucre.Disposable
import de.sciss.lucre.Txn.{peer => txPeer}
import de.sciss.lucre.synth.{Buffer, RT, Server, Synth}
import de.sciss.osc
import de.sciss.proc.AuralSystem
import de.sciss.proc.impl.StreamBuffer
import de.sciss.synth.proc.graph.impl.SendReplyResponder
import de.sciss.synth.ugen
import de.sciss.synth.{SynthGraph, intNumberWrapper}

import scala.annotation.tailrec
import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.{Ref, TxnExecutor}
import scala.math.{max, min}
import scala.util.control.NonFatal

object PhysicalOut {
  def apply(indices: OutI, in: ISeq[OutD], auralSystem: AuralSystem)(implicit b: Builder): Unit = {
    val sink = new Stage(layer = b.layer, numChannels = in.size, auralSystem = auralSystem)
    val stage = b.add(sink)
    // XXX TODO: handle `indices`
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "PhysicalOut"

  private type Shp = UniformSinkShape[BufD]

  private final class Stage(layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit protected val ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = UniformSinkShape[BufD](
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, numChannels = numChannels,
        auralSystem = auralSystem)
  }

  private final class Logic(shape: Shp, layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit ctrl: Control)
    extends Handlers(name, layer, shape)
      with NodeHasInitImpl { logic =>

    private[this] var writtenCircle = 0
    private[this] var readCircle    = 0
    private[this] var circleSizeH   = 0
    private[this] var circleSize    = 0

    private[this] var obsAural : Disposable[RT] = _

    private[this] val hIns: Array[InDMain] = Array.tabulate(numChannels)(ch => InDMain(this, shape.inlets(ch)))

    private[this] final val SAMPLES_PER_BUF_SET = 1608 // ensure < 8K OSC bundle size, divisible by common num-channels
    private[this] val rtBufSz   = SAMPLES_PER_BUF_SET / numChannels
    private[this] val rtBufSmp  = rtBufSz * numChannels
    private[this] val rtBuf     = new Array[Float](rtBufSmp)
    private[this] var rtBufOff  = 0 // in frames
    private[this] var _stopped  = false

    private[this] val synBuf    = Ref.make[Buffer.Modifiable]()
    private[this] val trigResp  = Ref.make[TrigResp]()

    override protected def onDone(inlet: Inlet[_]): Unit = {
//      println(s"onDone($inlet)")
      completeStage()
    }

    // ---- StageLogic

    override protected def init(): Unit = {
      // super.init()
      logStream.info(s"$this - init()")
    }

    private def atomic[A](fun: RT => A): A = TxnExecutor.defaultAtomic { implicit itx =>
      implicit val rt: RT = RT.wrap(itx)
      fun(rt)
    }

    private final class TrigResp(protected val synth: Synth) extends SendReplyResponder {
      override protected def added()(implicit tx: RT): Unit = ()

      private[this] final val replyName = "/$str_fsc" // XXX TODO: make public: StreamBuffer.replyName("fsc")
      private[this] final val nodeId    = synth.peer.id

      override protected val body: Body = {
        case osc.Message(`replyName`, `nodeId`, _ /*`idx`*/, trigValF: Float) =>
//          println(s"RECEIVED TR $trigValF...")
          // logAural(m.toString)
          val trigVal = trigValF.toInt
          async {
            if (!_stopped) {
              logStream.debug(s"TrigResp($nodeId): $trigVal")
              readCircle = (trigVal + 2) * circleSizeH
              process()
            }
          }
      }

      override def dispose()(implicit tx: RT): Unit = {
        super.dispose()
        synth.dispose()
      }
    }

    // N.B.: not on the Akka thread
    private def startRT(s: Server)(implicit tx: RT): Unit = {
      val rtBufSz2 = rtBufSz << 1
      val bufSizeS = {
        val bufDur    = 1.5
        val blockSz   = s.config.blockSize
        val sr        = s.sampleRate
        // ensure half a buffer is a multiple of rtBufSz
        val minSz     = max(rtBufSz2.nextPowerOfTwo, 2 * blockSz)
        val bestSz    = max(minSz, (bufDur * sr).toInt)
        val bestSzLo  = bestSz - (bestSz % rtBufSz2)
        val bestSzHi  = bestSzLo << 1
        if (bestSzHi.toDouble/bestSz < bestSz.toDouble/bestSzLo) bestSzHi else bestSzLo
      }
      val b = Buffer(s)(numFrames = bufSizeS, numChannels = numChannels)
      synBuf() = b
      val g = SynthGraph {
        import ugen._
        import de.sciss.synth.Ops.stringToControl
        val bus     = "out".ir(0)
        val bufGE   = "buf".ir
        val sig     = StreamBuffer.makeUGen(key = "fsc", idx = 0, buf = bufGE, numChannels = numChannels,
          speed = 1.0, interp = 1)
        Out.ar(bus, sig)  // XXX TODO or use PhysicalOut so eventually we mute exceeding buses?
      }
      // note: we can start right away with silent buffer
      val _syn = Synth.play(g, nameHint = Some(name))(target = s.defaultGroup, args = "buf" -> b.id :: Nil,
        dependencies = b :: Nil)
      _syn.onEndTxn { implicit tx => b.dispose() }
      val _trigResp = new TrigResp(_syn)
      trigResp() = _trigResp

      tx.afterCommit {
        async {
          circleSizeH   = bufSizeS / rtBufSz2
          circleSize    = circleSizeH << 1
//          logStream.info(s"$this - startRT. bufSizeS = $bufSizeS, circleSizeH = $circleSizeH")
          logStream.debug(s"$this - startRT. bufSizeS = $bufSizeS, circleSizeH = $circleSizeH")
          writtenCircle = circleSizeH
          readCircle    = circleSize
          process()
        }
      }

      _trigResp.add()
    }

    override protected def launch(): Unit = {
      super.launch()

      obsAural = atomic { implicit tx =>
        auralSystem.reactNow { implicit tx => {
          case AuralSystem.Running(s) =>
            try {
              startRT(s)
            } catch {
              case NonFatal(ex: Exception) =>
//                ex.printStackTrace()
                failAsync(ex)
            }
          case _ =>
        }}
      }
    }

    override protected def stopped(): Unit = {
      logStream.info(s"$this - postStop()")
      _stopped = true

      if (obsAural != null) {
        atomic { implicit tx =>
          obsAural.dispose()
          obsAural = null
          val _trigResp = trigResp.swap(null)
          if (_trigResp != null) _trigResp.dispose()
        }
      }
    }

    @tailrec
    override protected def process(): Unit = {
      var stateChanged = false

      var ch      = 0
      var readSz  = 0
      while (ch < numChannels) {
        val hIn = hIns(ch)
        readSz  = if (ch == 0) hIn.available else min(readSz, hIn.available)
        ch += 1
      }

      val _rtBuf    = rtBuf
      val _rtBufOff = rtBufOff
      val chunkRt   = min(readSz, rtBufSz - _rtBufOff)
      val hasChunk  = chunkRt > 0

      logStream.debug(s"process() $this - readSz $readSz, chunkRt $chunkRt, rtBufOff ${_rtBufOff}")

      if (hasChunk) {
        val numCh   = numChannels
        var smpOff  = _rtBufOff * numChannels
        ch = 0
        while (ch < numChannels) {
          val hIn   = hIns(ch)
          val a     = hIn.array
          var i     = hIn.offset
          val stop  = i + chunkRt
          var j     = smpOff
          while (i < stop) {
            _rtBuf(j) = a(i).toFloat
            i += 1
            j += numCh
          }
          ch += 1
          smpOff += 1
        }
      }

      val rtBufOffNew = _rtBufOff + chunkRt
      if (rtBufOffNew == rtBufSz && writtenCircle < readCircle) {
        val bOff = (writtenCircle % circleSize) * rtBufSmp
        // XXX TODO: we could optimise this by sending a ByteBuffer directly via OSC
        // also `toIndexedSeq` creates an unnecessary array copy here
        logStream.debug(s"b.setn($bOff) - writtenCircle $writtenCircle")
        atomic { implicit tx =>
          val b = synBuf()
          b.setn((bOff, _rtBuf.toIndexedSeq))
        }
        writtenCircle += 1
        rtBufOff       = 0
        stateChanged   = true
      } else if (hasChunk) {
        rtBufOff = rtBufOffNew
      }

      if (hasChunk) {
        ch = 0
        while (ch < numChannels) {
          hIns(ch).advance(chunkRt)
          ch += 1
        }
        stateChanged = true
      }

      if (stateChanged) process()
    }
  }
}