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
import akka.stream.{ActorAttributes, Attributes, Inlet, SinkShape}
import de.sciss.file._
import de.sciss.fscape.stream.{logStream => log}
import de.sciss.synth.io

import scala.util.control.NonFatal

final class AudioFileSink(f: File, spec: io.AudioFileSpec, ctrl: Control)
  extends GraphStage[SinkShape[BufD]] { sink =>
  
  private[this] val in = Inlet[BufD]("AudioFileSink.in")

  override val shape = SinkShape(in)

  override def initialAttributes: Attributes =
    Attributes.name(toString) and
    ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler {

    private[this] var af      : io.AudioFile = _
    private[this] var buf     : io.Frames = _
    private[this] var bufSize : Int = _

    private[this] var framesWritten = 0L

    setHandler(in, this)

    override def preStart(): Unit = {
      log(s"$sink - preStart()")
      require(spec.numChannels == 1, s"$this: Currently only monophonic files supported")
      af            = io.AudioFile.openWrite(f, spec)
      bufSize       = ctrl.bufSize
      buf           = af.buffer(bufSize)
      pull(in)
    }

    override def postStop(): Unit = {
      log(s"$sink - postStop()")
      buf = null
      try {
        af.close()
      } catch {
        case NonFatal(ex) => // XXX TODO --- what?
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
      af.write(buf, 0, chunk)
      framesWritten += chunk

      ctrl.returnBufD(bufIn)
      pull(in)
    }
  }
  override def toString = s"AudioFileSink(${f.name})"
}