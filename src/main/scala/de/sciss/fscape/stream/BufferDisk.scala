/*
 *  BufferDisk.scala
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

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.BlockingGraphStage
import de.sciss.synth.io.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}

import scala.util.control.NonFatal

object BufferDisk {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final class Stage(implicit ctrl: Control)
    extends BlockingGraphStage[FlowShape[BufD, BufD]] {

    override def toString = "BufferDisk"

    val shape = new FlowShape(
      in  = InD ("BufferDisk.in" ),
      out = OutD("BufferDisk.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: FlowShape[BufD, BufD])(implicit ctrl: Control)
    extends GraphStageLogic(shape) with InHandler with OutHandler {

    private[this] var af: AudioFile = _
    private[this] var bufSize: Int = _
    private[this] var buf: Array[Array[Float]] = _

    private[this] var framesWritten = 0L
    private[this] var framesRead    = 0L

    setHandlers(shape.in, shape.out, this)

    override def preStart(): Unit = {
      val f     = ctrl.createTempFile()
      // XXX TODO --- should we support 64 bit?
      // Note: sample-rate is not used
      af        = AudioFile.openWrite(f, AudioFileSpec(AudioFileType.Wave64, SampleFormat.Float,
        numChannels = 1, sampleRate = 44100.0))
      bufSize   = ctrl.bufSize
      buf       = af.buffer(bufSize)
      pull(shape.in)
    }

    override def postStop(): Unit = {
      buf = null
      try {
        af.close()
        af.file.get.delete()
      } catch {
        case NonFatal(ex) =>  // XXX TODO -- what with this?
      }
    }

    def onPush(): Unit = {
      val bufIn = grab(shape.in)
      tryPull(shape.in)
      val chunk = bufIn.size
      println(s"BufferDisk.onPush($chunk)")

      var i = 0
      val a = bufIn.buf
      val b = buf(0)
      while (i < chunk) {
        b(i) = a(i).toFloat
        i += 1
      }

      try {
        if (af.position != framesWritten) af.position = framesWritten
        af.write(buf, 0, chunk)
        framesWritten += chunk
        println(s"framesWritten = $framesWritten")
      } finally {
        bufIn.release()
      }

      if (isAvailable(shape.out)) onPull()
    }

    def onPull(): Unit = {
      val chunk = math.min(bufSize, framesWritten - framesRead).toInt
      println(s"BufferDisk.onPull($chunk)")
      if (chunk == 0) {
        if (isClosed(shape.in)) completeStage()

      } else {
        if (af.position != framesRead) af.position = framesRead
        af.read(buf, 0, chunk)
        framesRead += chunk
        println(s"framesRead    = $framesRead")

        val bufOut = ctrl.borrowBufD()
        val b = bufOut.buf
        val a = buf(0)
        var i = 0
        while (i < chunk) {
          b(i) = a(i).toDouble
          i += 1
        }
        bufOut.size = chunk
        push(shape.out, bufOut)
      }
    }

    // in closed
    override def onUpstreamFinish(): Unit = {
      println("BufferDisk.onUpstreamFinish")
      if (isAvailable(shape.out)) onPull()
    }
  }
}