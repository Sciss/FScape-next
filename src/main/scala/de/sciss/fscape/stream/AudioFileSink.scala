/*
 *  AudioFileSink.scala
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

package de.sciss.fscape.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import akka.stream.{ActorAttributes, Attributes}
import de.sciss.file._
import de.sciss.fscape.stream.impl.UniformSinkShape
import de.sciss.fscape.stream.{logStream => log}
import de.sciss.synth.io

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

final class AudioFileSink(f: File, spec: io.AudioFileSpec)(implicit ctrl: Control)
  extends GraphStage[UniformSinkShape[BufD]] { sink =>
  
  override val shape = UniformSinkShape[BufD](Vector.tabulate(spec.numChannels)(ch => InD(s"AudioFileSink.in$ch")))

  override def initialAttributes: Attributes =
    Attributes.name(toString) and
    ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler { logic =>

    private[this] var af      : io.AudioFile = _
    private[this] var buf     : io.Frames = _

    private[this] var framesWritten = 0L
    private[this] var pushed        = 0
    private[this] val numChannels   = spec.numChannels
    private[this] val bufIns        = new Array[BufD](spec.numChannels)

    private /* [this] */ val result = Promise[Long]()

    shape.inlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      val asyncCancel = getAsyncCallback[Unit] { _ =>
        val ex = Cancelled()
        if (result.tryFailure(ex)) failStage(ex)
      }
      ctrl.addLeaf(new Leaf {
        def result: Future[Any] = logic.result.future

        def cancel(): Unit = asyncCancel.invoke(())
      })

      log(s"$sink - preStart()")
      af = io.AudioFile.openWrite(f, spec)
      shape.inlets.foreach(pull)
    }

    override def postStop(): Unit = {
      log(s"$sink - postStop()")
      buf = null
      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        ch += 1
      }
      try {
        af.close()
        result.trySuccess(framesWritten)
      } catch {
        case NonFatal(ex) => result.tryFailure(ex)
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
        framesWritten += chunk
      } catch {
        case NonFatal(ex) =>
          result.failure(ex)
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
      result.failure(ex)
      super.onUpstreamFailure(ex)
    }
  }

  override def toString = s"AudioFileSink(${f.name})"
}