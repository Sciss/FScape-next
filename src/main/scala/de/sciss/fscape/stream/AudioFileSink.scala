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
import akka.stream.{ActorAttributes, Attributes, SinkShape}
import de.sciss.file._
import de.sciss.fscape.stream.{logStream => log}
import de.sciss.synth.io

import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

final class AudioFileSink(f: File, spec: io.AudioFileSpec)(implicit ctrl: Control)
  extends GraphStage[SinkShape[BufD]] { sink =>
  
  private[this] val in = InD("AudioFileSink.in")

  override val shape = SinkShape(in)

  override def initialAttributes: Attributes =
    Attributes.name(toString) and
    ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler { logic =>

    private[this] var af      : io.AudioFile = _
    private[this] var buf     : io.Frames = _

    private[this] var framesWritten = 0L

    private /* [this] */ val result = Promise[Long]()

    setHandler(in, this)

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
      require(spec.numChannels == 1, s"$this: Currently only monophonic files supported")
      af = io.AudioFile.openWrite(f, spec)
      pull(in)
    }

    override def postStop(): Unit = {
      log(s"$sink - postStop()")
      buf = null
      try {
        af.close()
        result.trySuccess(framesWritten)
      } catch {
        case NonFatal(ex) => result.tryFailure(ex)
      }
    }

    override def onPush(): Unit = {
      val bufIn = grab(in)
      val chunk = bufIn.size
      if (buf == null || buf(0).length < chunk) {
        buf = af.buffer(chunk)
      }
      var i = 0
      val a = bufIn.buf
      val b = buf(0)
      while (i < chunk) {
        b(i) = a(i).toFloat
        i += 1
      }
      try {
        af.write(buf, 0, chunk)
        framesWritten += chunk
      } catch {
        case NonFatal(ex) =>
          result.failure(ex)
          failStage(ex)
      } finally {
        bufIn.release()
      }
      pull(in)
    }

    override def onUpstreamFailure(ex: Throwable): Unit = {
      result.failure(ex)
      super.onUpstreamFailure(ex)
    }
  }

  override def toString = s"AudioFileSink(${f.name})"
}