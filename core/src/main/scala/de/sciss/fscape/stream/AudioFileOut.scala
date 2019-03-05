/*
 *  AudioFileOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, UniformFanInShape}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeImpl}
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileSpec

import scala.collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal

object AudioFileOut {
  def apply(file: File, spec: AudioFileSpec, in: ISeq[OutD])(implicit b: Builder): OutL = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(file, spec)
    val stage = b.add(sink)
    (in zip stage.inSeq).foreach { case (output, input) =>
      b.connect(output, input)
    }
    stage.out
  }

  private final val name = "AudioFileOut"

  private type Shape = UniformFanInShape[BufD, BufL]

  private final class Stage(f: File, spec: io.AudioFileSpec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape: Shape = UniformFanInShape[BufD, BufL](
      OutL(s"$name.out"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")): _*
    )

    def createLogic(attr: Attributes) = new Logic(shape, f, spec)
  }

  private final class Logic(shape: Shape, protected val file: File, protected val spec: io.AudioFileSpec)
                           (implicit ctrl: Control)
    extends NodeImpl(s"$name(${file.name})", shape) with AbstractLogic {
  }

  trait AbstractLogic extends Node with OutHandler { logic: GraphStageLogic =>
    // ---- abstract ----

    protected def file : File
    protected def spec : io.AudioFileSpec
    protected def shape: Shape

    // ---- impl ----

    private[this] var af      : io.AudioFile = _
    private[this] var buf     : io.Frames = _

    private[this] var pushed        = 0
    private[this] val numChannels   = spec.numChannels
    private[this] val bufIns        = new Array[BufD](spec.numChannels)

    private[this] var shouldStop    = false
    private[this] var _isSuccess    = false

    protected final def isSuccess     : Boolean  = _isSuccess
    protected final def framesWritten : Long     = af.numFrames

    {
      val ins = shape.inSeq
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
        if (pushed == numChannels && isAvailable(shape.out)) process()
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

    override def preStart(): Unit = {
      logStream(s"$this - preStart()")
      af = io.AudioFile.openWrite(file, spec)
      shape.inlets.foreach(pull(_))
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
        af.close()
        // resultP.trySuccess(af.numFrames)
      // } catch {
      //   case NonFatal(ex) => resultP.tryFailure(ex)
      // }
    }

    final def onPull(): Unit =
      if (pushed == numChannels) process()

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
          failStage(ex)
      } finally {
        ch = 0
        while (ch < numChannels) {
          bufIns(ch).release()
          ch += 1
        }
      }

      val bufOut  = control.borrowBufL()
      val arrOut  = bufOut.buf
      var j = 0
      while (j < chunk) {
        arrOut(j) = pos1 + j
        j += 1
      }
      bufOut.size = chunk
      push(shape.out, bufOut)

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