/*
 *  DiskIn.scala
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

import akka.stream.Attributes
import akka.stream.stage.{GraphStageLogic, OutHandler}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, StageLogicImpl, UniformSourceShape}
import de.sciss.synth.io

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.control.NonFatal

object DiskIn {
  def apply(file: File, numChannels: Int)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(file, numChannels = numChannels)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "DiskIn"

  private type Shape = UniformSourceShape[BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(f: File, numChannels: Int)(implicit ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")))

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape, f, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, f: File, numChannels: Int)(implicit ctrl: Control)
    extends StageLogicImpl(s"$name(${f.name})", shape) with OutHandler {

    private[this] var af        : io.AudioFile  = _
    private[this] var buf       : io.Frames     = _
    private[this] var bufSize   : Int           = _

    private[this] var framesRead  = 0L

    shape.outlets.foreach(setHandler(_, this))

    override def preStart(): Unit = {
      logStream(s"preStart() $this")
      af          = io.AudioFile.openRead(f)
      if (af.numChannels != numChannels) {
        Console.err.println(s"Warning: DiskIn - channel mismatch (file has ${af.numChannels}, UGen has $numChannels)")
      }
      bufSize     = ctrl.blockSize
      buf         = af.buffer(bufSize)
    }

    override def postStop(): Unit = {
      logStream(s"postStop() $this")
      buf = null
      try {
        af.close()
      } catch {
        case NonFatal(ex) =>  // XXX TODO -- what with this?
      }
    }

    override def onDownstreamFinish(): Unit =
      if (shape.outlets.forall(isClosed(_))) {
        logStream(s"completeStage() $this")
        completeStage()
      }

    override def onPull(): Unit =
      if (numChannels == 1 || shape.outlets.forall(out => isClosed(out) || isAvailable(out))) process()

    private def process(): Unit = {
      val chunk = math.min(bufSize, af.numFrames - framesRead).toInt
      if (chunk == 0) {
        logStream(s"completeStage() $this")
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
//            println(s"disk   : ${bufOut.hashCode.toHexString} - ${bufOut.buf.toVector.hashCode.toHexString}")
            push(out, bufOut)
          }
          ch += 1
        }
      }
    }
  }
}