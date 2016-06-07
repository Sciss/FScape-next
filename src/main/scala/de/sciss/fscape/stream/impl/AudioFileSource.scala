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

package de.sciss.fscape
package stream
package impl

import akka.stream.Attributes
import akka.stream.stage.{GraphStageLogic, OutHandler}
import de.sciss.file._
import de.sciss.fscape.stream.{logStream => log}
import de.sciss.synth.io

import scala.util.control.NonFatal

// similar to internal `UnfoldResourceSource`
final class AudioFileSource(f: File, numChannels: Int)(implicit protected val ctrl: Control)
  extends BlockingGraphStage[UniformSourceShape[BufD]] { source =>

  override val shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"AudioFileSource.out$ch")))

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    private[this] var af        : io.AudioFile  = _
    private[this] var buf       : io.Frames     = _
    private[this] var bufSize   : Int           = _

    private[this] var framesRead  = 0L

    shape.outlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      log(s"$source - preStart()")
      af          = io.AudioFile.openRead(f)
      if (af.numChannels != numChannels) {
        Console.err.println(s"Warning: DiskIn - channel mismatch (file has ${af.numChannels}, UGen has $numChannels)")
      }
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

    override def onDownstreamFinish(): Unit =
      if (shape.outlets.forall(isClosed(_))) completeStage()

    override def onPull(): Unit =
      if (numChannels == 1 || shape.outlets.forall(out => isClosed(out) || isAvailable(out))) process()

    private def process(): Unit = {
      val chunk = math.min(bufSize, af.numFrames - framesRead).toInt
      if (chunk == 0) {
        completeStage()
      } else {
        af.read(buf, 0, chunk)
        framesRead += chunk
        var ch = 0
        while (ch < numChannels) {
          val out = shape.out(ch)
          if (!isClosed(out)) {
            val bufOut = ctrl.borrowBufD()
            val b = bufOut.buf
            if (ch < buf.length) {
              val a = buf(ch)
              var i = 0
              while (i < chunk) {
                b(i) = a(i).toDouble
                i += 1
              }
            } else {
              Util.clear(b, 0, chunk)
            }
            bufOut.size = chunk
            push(out, bufOut)
          }
          ch += 1
        }
      }
    }
  }
  override def toString = s"AudioFileSource(${f.name})"
}