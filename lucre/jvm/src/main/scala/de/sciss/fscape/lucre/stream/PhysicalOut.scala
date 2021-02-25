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
import de.sciss.numbers.Implicits._
import de.sciss.osc
import de.sciss.proc.AuralSystem
import de.sciss.proc.impl.StreamBuffer
import de.sciss.synth.proc.graph.impl.SendReplyResponder
import de.sciss.synth.{SynthGraph, ugen, Buffer => SBuffer}

import scala.annotation.tailrec
import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.{Ref, TxnExecutor}
import scala.math.{max, min}
import scala.util.control.NonFatal

object PhysicalOut {
  // XXX TODO: `index` currently unused
  def apply(index: OutI, in: ISeq[OutD], auralSystem: AuralSystem)(implicit b: Builder): Unit = {
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
      new Logic(shape, layer = layer, numChannels = numChannels, auralSystem = auralSystem)
  }

  private final class Logic(shape: Shp, layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit ctrl: Control)
    extends Handlers(name, layer, shape)
      with NodeHasInitImpl { logic =>

    private[this] var clientCircle  = 0
    private[this] var serverCircle  = 0
    private[this] var circleSizeH   = 0
    private[this] var circleSize    = 0

    private[this] var obsAural : Disposable[RT] = _

    private[this] val hIns: Array[InDMain] = Array.tabulate(numChannels)(ch => InDMain(this, shape.inlets(ch)))

    private[this] final val SAMPLES_PER_BUF_SET = 1608 // ensure < 8K OSC bundle size, divisible by common num-channels
    private[this] val oscBufSz  = SAMPLES_PER_BUF_SET / numChannels
    private[this] val oscBufSmp = oscBufSz * numChannels
    private[this] val rtBufSz   = oscBufSz
    private[this] val rtBufSmp  = oscBufSmp
    private[this] val rtBuf     = new Array[Float](rtBufSmp)
    private[this] var rtBufOff  = 0 // in frames
    private[this] var _stopped  = false

    private[this] val synBuf    = Ref.make[Buffer.Modifiable]()
    private[this] val trigResp  = Ref.make[TrigResp]()
    private[this] var synBufPeer: SBuffer = _

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

    private[this] final val replyName = "/$fsc"

    private final class TrigResp(protected val synth: Synth) extends SendReplyResponder {
      override protected def added()(implicit tx: RT): Unit = ()

      private[this] final val nodeId = synth.peer.id
      private[this] var trigAdd = 2

      override protected val body: Body = {
        case osc.Message(`replyName`, `nodeId`, _ /*`idx`*/, trigValF: Float) =>
//          println(s"RECEIVED TR $trigValF...")
          // logAural(m.toString)
          val trigVal = trigValF.toInt
          async {
            if (!_stopped) {
              logStream.debug(s"TrigResp($nodeId): $trigVal")
              serverCircle = (trigVal + trigAdd) * circleSizeH
              // "handle" underflow, by simply readjusting the pointers
              while (clientCircle + circleSize < serverCircle) {
                trigAdd -= 1
                serverCircle -= circleSize
              }
              // println(s"Out TR: serverCircle now $serverCircle, clientCircle $clientCircle")
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
      val oscBufSz2 = oscBufSz << 1
      val bufSizeS = {
        val bufDur    = 1.5
        val blockSz   = s.config.blockSize
        val sr        = s.sampleRate
        // ensure half a buffer is a multiple of oscBufSz
        val minSz     = max(oscBufSz2.nextPowerOfTwo, 2 * blockSz)
        val bestSz    = max(minSz, (bufDur * sr).toInt)
        val bestSzLo  = bestSz - (bestSz % oscBufSz2)
        val bestSzHi  = bestSzLo << 1
        if (bestSzHi.toDouble/bestSz < bestSz.toDouble/bestSzLo) bestSzHi else bestSzLo
      }
      val b = Buffer(s)(numFrames = bufSizeS, numChannels = numChannels)
      synBuf() = b
      val g = SynthGraph {
        import de.sciss.synth.Ops.stringToControl
        import ugen._
        val bus     = "out".ir(0)
        val bufGE   = "buf".ir
        val phasor  = StreamBuffer.makeIndex(replyName = replyName, buf = bufGE)
        val sig     = BufRd.ar(numChannels, buf = bufGE, index = phasor, loop = 0, interp = 1)
        ugen.PhysicalOut.ar(bus, sig)
      }
      // note: we can start right away with silent buffer
      val _syn = Synth.play(g, nameHint = Some(name))(target = s.defaultGroup, args = "buf" -> b.id :: Nil,
        dependencies = b :: Nil)
      _syn.onEndTxn { implicit tx => b.dispose() }
      val _trigResp = new TrigResp(_syn)
      trigResp() = _trigResp

      tx.afterCommit {
        async {
          circleSizeH   = bufSizeS / oscBufSz2
          circleSize    = circleSizeH << 1
//          logStream.info(s"$this - startRT. bufSizeS = $bufSizeS, circleSizeH = $circleSizeH")
          logStream.debug(s"$this - startRT. bufSizeS = $bufSizeS, circleSizeH = $circleSizeH")
          clientCircle  = circleSizeH
          serverCircle  = circleSize
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
      while (ch < numChannels) {
        val hIn = hIns(ch)
        copySz  = if (ch == 0) hIn.available else min(copySz, hIn.available)
        ch += 1
      }

      val _rtBuf    = rtBuf
      val _rtBufOff = rtBufOff
      val chunkRt   = min(copySz, rtBufSz - _rtBufOff)
      val hasChunk  = chunkRt > 0

      logStream.debug(s"process() $this - copySz $copySz, chunkRt $chunkRt, rtBufOff ${_rtBufOff}")
      // println(s"Out process() - copySz $copySz, chunkRt $chunkRt, rtBufOff ${_rtBufOff}")

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
      if (rtBufOffNew == rtBufSz && clientCircle < serverCircle) {
        val bOff = (clientCircle % circleSize) * oscBufSmp
        // XXX TODO: we could optimise this by sending a ByteBuffer directly via OSC
        // also `toIndexedSeq` creates an unnecessary array copy here
        logStream.debug(s"b.setn($bOff) - clientCircle $clientCircle")
        // println(s"b.setn($bOff) - cci $clientCircle")
        val b = synBufPeer
        b.server.!(b.setnMsg((bOff, _rtBuf.toIndexedSeq)))
        clientCircle  += 1
        rtBufOff       = 0
        stateChanged   = true
      } else if (hasChunk) {
        rtBufOff = rtBufOffNew
      }

      if (hasChunk) {
        ch = 0
        while (ch < numChannels) {
          val hIn = hIns(ch)
          hIn.advance(chunkRt)
          ch += 1
        }
        stateChanged = true
      }

      if (stateChanged) process()
    }
  }
}