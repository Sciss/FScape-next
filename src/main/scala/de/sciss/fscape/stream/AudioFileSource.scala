/*
 *  AudioFileSource.scala
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

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{ActorAttributes, Attributes, SourceShape}
import de.sciss.file._
import de.sciss.fscape.stream.{logStream => log}
import de.sciss.synth.io

import scala.util.control.NonFatal

// similar to internal `UnfoldResourceSource`
final class AudioFileSource(f: File, ctrl: Control)
  extends GraphStage[SourceShape[BufD]] { source =>

  private[this] val out = OutD("AudioFileSource.out")
  
  override val shape = SourceShape(out)

  override def initialAttributes: Attributes =
    Attributes.name(toString) and
    ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    private[this] var af        : io.AudioFile  = _
    private[this] var buf       : io.Frames     = _
    private[this] var bufSize   : Int           = _

    private[this] var framesRead  = 0L

    setHandler(out, this)

    override def preStart(): Unit = {
      log(s"$source - preStart()")
      af          = io.AudioFile.openRead(f)
      bufSize     = ctrl.bufSize
      buf         = af.buffer(bufSize)
    }

    override def postStop(): Unit = {
      log(s"$source - postStop()")
      buf = null
      try {
        af.close()
      } catch {
        case NonFatal(ex) =>  // XXX TODO -- what with this?
      }
    }

    override def onPull(): Unit = {
      val chunk = math.min(bufSize, af.numFrames - framesRead).toInt
      if (chunk == 0) {
        completeStage()
      } else {
        af.read(buf, 0, chunk)
        framesRead += chunk
        val bufOut = ctrl.borrowBufD()
        val a = buf(0) // XXX TODO --- how to handle channels
        val b = bufOut.buf
        var i = 0
        while (i < chunk) {
          b(i) = a(i).toDouble
          i += 1
        }
        bufOut.size = chunk
        push(out, bufOut)
      }
    }
  }
  override def toString = s"AudioFileSource(${f.name})"
}