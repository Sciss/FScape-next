/*
 *  PhysicalIn.scala
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

package de.sciss.fscape.lucre
package stream

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.Handlers.OutDMain
import de.sciss.fscape.stream.impl.shapes.UniformSourceShape
import de.sciss.fscape.stream.impl.{AsyncTaskLogic, Handlers, NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufD, Builder, Control, Layer, OutD, OutI}
import de.sciss.lucre.Disposable
import de.sciss.lucre.Txn.{peer => txPeer}
import de.sciss.lucre.synth.{Buffer, RT, Server, Synth}
import de.sciss.numbers.Implicits._
import de.sciss.osc
import de.sciss.proc.AuralSystem
import de.sciss.proc.impl.StreamBuffer
import de.sciss.synth.message.BufferSetn
import de.sciss.synth.proc.graph.impl.SendReplyResponder
import de.sciss.synth.{SynthGraph, ugen, Buffer => SBuffer}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.{Ref, TxnExecutor}
import scala.math.{max, min}
import scala.util.control.NonFatal

object PhysicalIn {
  // XXX TODO: `index` currently unused
  def apply(index: OutI, numChannels: Int, auralSystem: AuralSystem)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(layer = b.layer, numChannels = numChannels, auralSystem = auralSystem)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "PhysicalIn"

  private type Shp = UniformSourceShape[BufD]

  private final class Stage(layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit protected val ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = UniformSourceShape[BufD](
      Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, numChannels = numChannels, auralSystem = auralSystem)
  }

  // XXX TODO DRY with PhysicalOut
  private final class Logic(shape: Shp, layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit ctrl: Control)
    extends Handlers[Shp](name, layer, shape)
      with NodeHasInitImpl with AsyncTaskLogic { logic =>

    private[this] var clientCircle  = 0
    private[this] var serverCircle  = 0
    private[this] var circleSizeH   = 0
    private[this] var circleSize    = 0

    private[this] var obsAural : Disposable[RT] = _

    private[this] val hOuts: Array[OutDMain] = Array.tabulate(numChannels)(ch => OutDMain(this, shape.outlets(ch)))

    private[this] final val SAMPLES_PER_BUF_SET = 1608 // ensure < 8K OSC bundle size, divisible by common num-channels
    private[this] val rtBufSz   = SAMPLES_PER_BUF_SET / numChannels
    private[this] val rtBufSmp  = rtBufSz * numChannels
    private[this] val rtBuf     = new Array[Float](rtBufSmp)
    private[this] var rtBufOff  = rtBufSz // in frames
    private[this] var _stopped  = false

    private[this] val synBuf    = Ref.make[Buffer.Modifiable]()
    private[this] val trigResp  = Ref.make[TrigResp]()
    private[this] var synBufPeer: SBuffer = _

    private[this] var numChannelsOpen = numChannels

    override protected def onDone(inlet: Inlet[_]): Unit = ()

    override protected def onDone(outlet: Outlet[_]): Unit = {
      numChannelsOpen -= 1
      val all = numChannelsOpen == 0
      if (all) {
        completeStage()
      } else {
        process()
      }
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

    private[this] final val replyName = "/$fsc"

    private final class TrigResp(protected val synth: Synth) extends SendReplyResponder {
      override protected def added()(implicit tx: RT): Unit = ()

      private[this] final val nodeId = synth.peer.id

      override protected val body: Body = {
        case osc.Message(`replyName`, `nodeId`, _ /*`idx`*/, trigValF: Float) =>
          //          println(s"RECEIVED TR $trigValF...")
          // logAural(m.toString)
          val trigVal = trigValF.toInt
          async {
            if (!_stopped) {
              logStream.debug(s"TrigResp($nodeId): $trigVal")
              serverCircle = trigVal * circleSizeH
              // println(s"In TR: serverCircle now $serverCircle, clientCircle $clientCircle")
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
        import de.sciss.synth.Ops.stringToControl
        import ugen._
        val bus     = "in".ir(0)
        val bufGE   = "buf".ir
        val sig     = ugen.PhysicalIn.ar(bus, numChannels)
        val phasor  = StreamBuffer.makeIndex(replyName = replyName, buf = bufGE)
        BufWr.ar(sig, buf = bufGE, index = phasor, loop = 0)
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
          synBufPeer    = b.peer
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
      var copySz  = 0
      var hasCopySz = false
      while (ch < numChannels) {
        val hOut = hOuts(ch)
        if (!hOut.isDone) {
          copySz = if (!hasCopySz) {
            hasCopySz = true
            hOut.available
          } else {
            min(copySz, hOut.available)
          }
        }
        ch += 1
      }

      val _rtBuf    = rtBuf
      val _rtBufOff = rtBufOff
      val chunkRt   = min(copySz, rtBufSz - _rtBufOff)
      val hasChunk  = chunkRt > 0

      logStream.debug(s"process() $this - copySz $copySz, chunkRt $chunkRt, rtBufOff ${_rtBufOff}")

      if (hasChunk) {
        val numCh   = numChannels
        var smpOff  = _rtBufOff * numChannels
        ch = 0
        while (ch < numChannels) {
          val hOut  = hOuts(ch)
          val a     = hOut.array
          var i     = hOut.offset
          val stop  = i + chunkRt
          var j     = smpOff
          while (i < stop) {
            a(i) = _rtBuf(j).toDouble
            i += 1
            j += numCh
          }
          ch += 1
          smpOff += 1
        }
      }

      val rtBufOffNew = _rtBufOff + chunkRt
      if (rtBufOffNew == rtBufSz && clientCircle < serverCircle && !taskBusy) {
        rtBufOff = rtBufOffNew
        val bOff = (clientCircle % circleSize) * rtBufSmp
        logStream.debug(s"b.getn($bOff) - clientCircle $clientCircle")
        val b = synBufPeer
        task("getn") {
          b.server.!!(b.getnMsg(bOff until (bOff + rtBufSmp))) {
            case BufferSetn(b.id, (`bOff`, xs)) if xs.length == rtBufSmp => xs
          }
        } { xs =>
          xs.copyToArray(rtBuf, 0, xs.length)
          clientCircle  += 1
          rtBufOff       = 0
        }

      } else if (hasChunk) {
        rtBufOff = rtBufOffNew
      }

      if (hasChunk) {
        ch = 0
        while (ch < numChannels) {
          val hOut = hOuts(ch)
          if (!hOut.isDone) hOuts(ch).advance(chunkRt)
          ch += 1
        }
        stateChanged = true
      }

      if (stateChanged) process()
    }

    override protected def taskPending(): Unit = process()
  }
}