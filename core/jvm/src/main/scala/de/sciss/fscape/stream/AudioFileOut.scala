/*
 *  AudioFileOut.scala
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

import java.net.URI

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, UniformFanInShape}
import de.sciss.audiofile.AudioFile.Frames
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl}
import de.sciss.audiofile.{AudioFile, AudioFileSpec}

import scala.collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal

object AudioFileOut {
  def apply(uri: URI, spec: AudioFileSpec, in: ISeq[OutD])(implicit b: Builder): OutL = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val nameL = Util.mkLogicName(name, uri)
    val sink  = new Stage(layer = b.layer, uri = uri, spec = spec, nameL = nameL)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
    stage.out
  }

  private final val name = "AudioFileOut"

  private type Shp = UniformFanInShape[BufD, BufL]

  private final class Stage(layer: Layer, uri: URI, spec: AudioFileSpec, nameL: String)
                           (implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](nameL) {

    val shape: Shape = UniformFanInShape[BufD, BufL](
      OutL(s"$name.out"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")): _*
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(nameL, shape, layer = layer, uri = uri, spec = spec)
  }

  private final class Logic(name: String, shape: Shp, layer: Layer, protected val uri: URI, protected val spec: AudioFileSpec)
                           (implicit ctrl: Control)
    extends AbstractLogic(name, layer, shape)

  abstract class AbstractLogic(name: String, layer: Layer, shape: Shp)(implicit control: Control)
    extends NodeImpl[Shp](name, layer, shape)
    with NodeHasInitImpl with OutHandler {

    logic: NodeImpl[Shp] =>

    // ---- abstract ----

    protected def uri : URI
    protected def spec: AudioFileSpec

//    def shape: Shape

    // ---- impl ----

    private[this] var af      : AudioFile = _
    private[this] var buf     : Frames = _

    private[this] var pushed        = 0
    private[this] val numChannels   = spec.numChannels
    private[this] val bufIns        = new Array[BufD](spec.numChannels)

    private[this] var shouldStop    = false
    private[this] var _isSuccess    = false

    protected final def isSuccess     : Boolean  = _isSuccess
    protected final def framesWritten : Long     = af.numFrames

    {
      val ins = shape.inlets
      var ch = 0
      while (ch < numChannels) {
        val in = ins(ch)
        setHandler(in, new InH(in /* , ch */))
        ch += 1
      }
    }
    setHandler(shape.out, this)

    private final class InH(in: InD /* , ch: Int */) extends InHandler {
      def onPush(): Unit = {
        pushed += 1
        if (canProcess) process()
      }

      override def onUpstreamFinish(): Unit = {
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
      val f = new File(uri)
      af = AudioFile.openWrite(f, spec)
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override protected def stopped(): Unit = {
      logStream(s"$this - postStop()")
      buf = null
      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        ch += 1
      }
      // try {
      if (af != null) {
        af.close()
        af = null
      }
        // resultP.trySuccess(af.numFrames)
      // } catch {
      //   case NonFatal(ex) => resultP.tryFailure(ex)
      // }
    }

    private def canProcess: Boolean =
      pushed == numChannels && (isClosed(shape.out) || isAvailable(shape.out))

    final def onPull(): Unit =
      if (isInitialized && canProcess) process()

    // we do not care if the consumer of the frame information closes early.
    override def onDownstreamFinish(cause: Throwable): Unit =
      onPull()

    private def process(): Unit = {
//      logStream(s"process() $this")
      logStream(s"process() $this")
      pushed = 0

      var ch = 0
      var chunk = 0
      while (ch < numChannels) {
        val bufIn = grab(shape.in(ch))
        bufIns(ch)  = bufIn
        chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
        ch += 1
      }

      if (buf == null || buf(0).length < chunk) {
        buf = af.buffer(chunk)
      }

      val pos1 = af.position + 1

      ch = 0
      while (ch < numChannels) {
        var i = 0
        val a = bufIns(ch).buf
        val b = buf(ch)
        while (i < chunk) {
          b(i) = a(i).toFloat
          i += 1
        }
        ch += 1
      }
      try {
        af.write(buf, 0, chunk)
      } catch {
        case NonFatal(ex) =>
//          resultP.failure(ex)
          notifyFail(ex)
      } finally {
        ch = 0
        while (ch < numChannels) {
          bufIns(ch).release()
          ch += 1
        }
      }

      if (!isClosed(shape.out)) {
        val bufOut  = control.borrowBufL()
        val arrOut  = bufOut.buf
        var j = 0
        while (j < chunk) {
          arrOut(j) = pos1 + j
          j += 1
        }
        bufOut.size = chunk
        push(shape.out, bufOut)
      }

      if (shouldStop) {
        _isSuccess = true
        completeStage()
      } else {
        ch = 0
        while (ch < numChannels) {
          pull(shape.in(ch))
          ch += 1
        }
      }
    }

//    override def onUpstreamFailure(ex: Throwable): Unit = {
//      resultP.failure(ex)
//      super.onUpstreamFailure(ex)
//    }
  }
}