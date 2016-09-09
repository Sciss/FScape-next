/*
 *  AudioFileOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{GraphStageLogic, InHandler}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, LeafStage, StageLogicImpl, UniformSinkShape}
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileSpec

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

object AudioFileOut {
  def apply(file: File, spec: AudioFileSpec, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(file, spec)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "AudioFileOut"

  private type Shape = UniformSinkShape[BufD]

  private final class Stage(f: File, spec: io.AudioFileSpec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") with LeafStage {

    override val shape = UniformSinkShape[BufD](Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")))

    protected def createLeaf(): GraphStageLogic with Leaf =
      new Logic(shape, f, spec)
  }

  private final class Logic(shape: Shape, f: File, spec: io.AudioFileSpec)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${f.name})", shape) with InHandler with Leaf { logic =>

    private[this] var af      : io.AudioFile = _
    private[this] var buf     : io.Frames = _

    private[this] var pushed        = 0
    private[this] val numChannels   = spec.numChannels
    private[this] val bufIns        = new Array[BufD](spec.numChannels)

    private /* [this] */ val resultP = Promise[Long]()

    shape.inlets.foreach(setHandler(_, this))

    // ---- Leaf

    private[this] val asyncCancel = getAsyncCallback[Unit] { _ =>
      val ex = Cancelled()
      if (resultP.tryFailure(ex)) failStage(ex)
    }

    def result: Future[Any] = resultP.future

    def cancel(): Unit = asyncCancel.invoke(())

    // ---- StageLogic

    override def preStart(): Unit = {
      logStream(s"$this - preStart()")
      af = io.AudioFile.openWrite(f, spec)
      shape.inlets.foreach(pull)
    }

    override def postStop(): Unit = {
      logStream(s"$this - postStop()")
      buf = null
      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        ch += 1
      }
      try {
        af.close()
        resultP.trySuccess(af.numFrames)
      } catch {
        case NonFatal(ex) => resultP.tryFailure(ex)
      }
    }

    override def onPush(): Unit = {
      pushed += 1
      if (pushed == numChannels) {
        pushed = 0
        process()
      }
    }

    private def process(): Unit = {
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
          resultP.failure(ex)
          failStage(ex)
      } finally {
        ch = 0
        while (ch < numChannels) {
          bufIns(ch).release()
          ch += 1
        }
      }

      ch = 0
      while (ch < numChannels) {
        pull(shape.in(ch))
        ch += 1
      }
    }

    override def onUpstreamFailure(ex: Throwable): Unit = {
      resultP.failure(ex)
      super.onUpstreamFailure(ex)
    }
  }
}