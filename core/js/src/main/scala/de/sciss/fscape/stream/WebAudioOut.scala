/*
 *  WebAudioOut.scala
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

package de.sciss.fscape
package stream

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.fscape.stream.impl.shapes.UniformSinkShape
import de.sciss.fscape.stream.impl.{AudioContextExt, AudioProcessingEvent, BlockingGraphStage, NodeHasInitImpl, NodeImpl, ScriptProcessorNode}
import org.scalajs.dom
import org.scalajs.dom.AudioContext

import scala.collection.immutable.{Seq => ISeq}
import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

object WebAudioOut {
  def apply(in: ISeq[OutD])(implicit b: Builder): Unit = {
    val sink = new Stage(layer = b.layer, numChannels = in.size)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "WebAudioOut"

  private type Shp = UniformSinkShape[BufD]

  private final class Stage(layer: Layer, numChannels: Int)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](name) {

    val shape: Shape = UniformSinkShape[BufD](
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, numChannels = numChannels)
  }

  import scala.language.implicitConversions

  implicit def AudioContextExt(context: AudioContext): AudioContextExt =
    context.asInstanceOf[AudioContextExt]

  private final class Logic(shape: Shp, layer: Layer, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl[Shp](name, layer, shape)
      with NodeHasInitImpl { logic =>

//    private[this] var buf     : io.Frames = _

    private[this] var pushed        = 0
    private[this] val bufIns        = new Array[BufD](numChannels)

    private[this] var shouldStop    = false
    private[this] var _isSuccess    = false

    private[this] var audioContext    : AudioContext        = _
    private[this] var scriptProcessor : ScriptProcessorNode = _

    private[this] val circleSize    = 3
    private[this] val rtBufCircle   = Array.fill(circleSize)(new Array[Float32Array](numChannels))
    private[this] var writtenCircle = 0
    private[this] var readCircle    = 0
    private[this] var readCircleRT  = 0

    private[this] var LAST_REP_PROC   = 0L
    private[this] var LAST_REP_PROC_C = 0
    private[this] var LAST_REP_IN     = 0L
    private[this] var LAST_REP_IN_C   = 0

    private[this] val DEBUG = true
    private[this] var okRT  = false

    private[this] val realtimeFun: js.Function1[AudioProcessingEvent, Unit] = { e =>
      if (okRT) { // there is a weird condition in which the realtime function is still called after `stopped`
        val b       = e.outputBuffer
        val numCh   = b.numberOfChannels
  //      val len     = b.length
        val rtBuf   = rtBufCircle(readCircleRT % circleSize)
        val newRead = readCircleRT + 1
        readCircleRT = newRead

        if (DEBUG) {
          val NOW = System.currentTimeMillis()
          val DT  = NOW - LAST_REP_PROC
          if (DT > 1000) {
            val len  = rtBuf(0).length
            val thru = ((newRead - LAST_REP_PROC_C) * len) * 1000.0 / DT
            println(s"<AudioProcessingEvent> buffers read = $readCircle; through-put is $thru Hz")
            LAST_REP_PROC   = NOW
            LAST_REP_PROC_C = newRead
          }
        }

        var ch = 0
        while (ch < numCh) {
          b.copyToChannel(rtBuf(ch), ch, 0)
          ch += 1
        }

        async {
          readCircle = newRead
          if (canProcess) process()
        }
      }
    }

    {
      val ins = shape.inlets
      var ch = 0
      while (ch < numChannels) {
        val in = ins(ch)
        setHandler(in, new InH(in /* , ch */))
        ch += 1
      }
    }

    private final class InH(in: InD /* , ch: Int */) extends InHandler {
      def onPush(): Unit = {
        pushed += 1
//        if (DEBUG) println(s"<$in> onPush; pushed = $pushed")
        if (canProcess) process()
      }

      override def onUpstreamFinish(): Unit = {
//        if (DEBUG) println(s"<$in> onUpstreamFinish")
        if (isAvailable(in)) {
          shouldStop = true
        } else {
          logStream(s"onUpstreamFinish($in)")
          _isSuccess = true
          super.onUpstreamFinish()
        }
      }
    }

    // ---- StageLogic

    override protected def init(): Unit = {
      // super.init()
      logStream(s"$this - init()")
    }

    override protected def launch(): Unit = {
      super.launch()
      audioContext    = new dom.AudioContext
      scriptProcessor = audioContext.createScriptProcessor(
        bufferSize              = control.blockSize,  // XXX TODO -- we could and should decouple this
        numberOfInputChannels   = 0,
        numberOfOutputChannels  = numChannels,
      )
      val bufSize = scriptProcessor.bufferSize

      if (DEBUG) println(s"WebAudioOut launch. WebAudio buffer size is $bufSize; FScape buffer size is ${control.blockSize}")

      var ch = 0
      while (ch < numChannels) {
        var ci = 0
        while (ci < circleSize) {
          rtBufCircle(ci)(ch) = new Float32Array(bufSize)
          ci += 1
        }
        ch += 1
      }
      okRT = true
      scriptProcessor.onaudioprocess = realtimeFun
      scriptProcessor.connect(audioContext.destination)
//      audioContext.resume()
    }

    override protected def stopped(): Unit = {
      logStream(s"$this - postStop()")
      okRT = false
      if (scriptProcessor != null) {
        scriptProcessor.disconnect(audioContext.destination)
        scriptProcessor = null
      }
      if (audioContext != null) {
        audioContext.close()
        audioContext = null
      }

//      buf = null
      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        var ci = 0
        while (ci < circleSize) {
          rtBufCircle(ci)(ch) = null
          ci += 1
        }
        ch += 1
      }
    }

    private def canProcess: Boolean =
      pushed == numChannels && writtenCircle < readCircle

    private def process(): Unit = {
      //      logStream(s"process() $this")
      logStream(s"process() $this")
      pushed = 0

      var ch = 0
      var chunk = 0
      while (ch < numChannels) {
        val bufIn = grab(shape.inlets(ch))
        bufIns(ch)  = bufIn
        chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
        ch += 1
      }

//      if (buf == null || buf(0).length < chunk) {
//        buf = ??? // af.buffer(chunk)
//      }

//      val pos1 = af.position + 1

      val rtBuf = rtBufCircle(writtenCircle % circleSize)
      val newWrite = writtenCircle + 1
      writtenCircle = newWrite

      if (DEBUG) {
        val NOW = System.currentTimeMillis()
        val DT  = NOW - LAST_REP_IN
        if (DT > 1000) {
          val len   = rtBuf(0).length
          val thru  = ((newWrite - LAST_REP_IN_C) * len) * 1000.0 / DT
          println(s"<process()> buffers written = $writtenCircle; through-put is $thru Hz")
          LAST_REP_IN   = NOW
          LAST_REP_IN_C = newWrite
        }
      }

      ch = 0
      while (ch < numChannels) {
        var i = 0
        val a = bufIns(ch).buf
        val b = rtBuf(ch)
        val CHUNK = math.min(chunk, b.length)
        while (i < CHUNK) {
          b(i) = a(i).toFloat
          i += 1
        }
        ch += 1
      }

      ch = 0
      while (ch < numChannels) {
        bufIns(ch).release()
        ch += 1
      }

      if (shouldStop) {
        _isSuccess = true
        completeStage()
      } else {
        // println("pulling inlets")
        ch = 0
        while (ch < numChannels) {
          pull(shape.inlets(ch))
          ch += 1
        }
      }
    }
  }
}