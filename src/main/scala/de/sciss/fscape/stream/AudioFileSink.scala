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

import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.Attributes._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import akka.stream.{ActorAttributes, Attributes, Inlet, SinkShape, Supervision}
import de.sciss.file._
import de.sciss.synth.io

import scala.annotation.tailrec
import scala.util.control.NonFatal

// similar to internal `UnfoldResourceSink`
final class AudioFileSink(f: File, spec: io.AudioFileSpec) 
  extends GraphStage[SinkShape[Double]] { sink =>
  
  val in = Inlet[Double]("AudioFileSink.in")

  override val shape = SinkShape(in)
  // override def initialAttributes: Attributes = DefaultAttributes.unfoldResourceSink

  private[this] val IODispatcher = ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")
  override def initialAttributes: Attributes = name("unfoldResourceSink") and IODispatcher

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler {
    private[this] lazy val decider = inheritedAttributes.get[SupervisionStrategy].map(_.decider)
      .getOrElse(Supervision.stoppingDecider)

    private[this] var af: io.AudioFile = _
    private[this] var buf: io.Frames = _
    private[this] final val bufSize = 8192
    private[this] var bufOff: Int = _
    private[this] var framesWritten: Long = _

    setHandler(in, this)

    override def preStart(): Unit = {
      println(s"${new java.util.Date()} $sink - preStart()")
      af            = io.AudioFile.openWrite(f, spec)
      buf           = af.buffer(bufSize)
      bufOff        = 0
      framesWritten = 0L
      pull(in)
    }

    @tailrec
    final override def onPush(): Unit = {
      // println("onPush")
      var resumingMode = false
      try {
        val bufFull = bufOff == bufSize
        if (bufFull) flush()
        val d = grab(in)
        buf(0)(bufOff) = d.toFloat // XXX TODO --- how to handle channels
        bufOff += 1
        pull(in)

      } catch {
        case NonFatal(ex) => decider(ex) match {
          case Supervision.Stop =>
            af.close()
            failStage(ex)
          case Supervision.Restart =>
            restartState()
            resumingMode = true
          case Supervision.Resume =>
            resumingMode = true
        }
      }
      if (resumingMode) onPush()
    }

    override def onUpstreamFinish(): Unit = {
      println(s"${new java.util.Date()} $sink.onUpstreamFinish()")
      closeStage()
    }

    private def restartState(): Unit = {
      af.close()
      preStart()
    }

    private def flush(): Unit =
      if (bufOff > 0) {
        af.write(buf, 0, bufOff)
        // println(s"$sink - flush ${af.file.orNull.name} - ${af.position}")
        bufOff = 0
      }

    private def closeStage(): Unit = {
      println(s"${new java.util.Date()} $sink - closeStage()")
      try {
        flush()
        af.close()
        completeStage()
      } catch {
        case NonFatal(ex) => failStage(ex)
      }
    }
  }
  override def toString = s"AudioFileSink(${f.name})"
}