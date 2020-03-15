/*
 *  AudioFileIn.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.lucre.stream

import akka.stream.Attributes
import akka.stream.stage.OutHandler
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl, UniformSourceShape}
import de.sciss.fscape.stream.{BufD, Control, Layer, OutD}
import de.sciss.fscape.{Util, logStream, stream}
import de.sciss.synth.io
import de.sciss.synth.proc.AudioCue

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.{Failure, Success, Try}

object AudioFileIn {
  def apply(cueTr: Try[AudioCue], numChannels: Int)(implicit b: stream.Builder): Vec[OutD] = {
    val name0   = cueTr match {
      case Success(c)   => c.artifact.name
      case Failure(ex)  => s"${ex.getClass}(${ex.getMessage})"
    }
    val name1   = s"$name($name0)"
    val source  = new Stage(layer = b.layer, cueTr = cueTr, numChannels = numChannels, name = name1)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "AudioFileIn"

  private type Shape = UniformSourceShape[BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(layer: Layer, cueTr: Try[AudioCue], numChannels: Int, name: String)
                           (implicit ctrl: Control)
    extends BlockingGraphStage[Shape](name) {

    val shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")))

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, cueTr = cueTr, name = name, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, layer: Layer, cueTr: Try[AudioCue], name: String, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with OutHandler {

    private[this] var af        : io.AudioFile  = _
    private[this] var buf       : io.Frames     = _
    private[this] var bufSize   : Int           = _

    private[this] var framesRead  = 0L
    private[this] var gain        = 1.0

    shape.outlets.foreach(setHandler(_, this))

    override protected def init(): Unit = {
      super.init()
      logStream(s"init() $this")
      cueTr match {
        case Success(cue) =>
          af          = io.AudioFile.openRead(cue.artifact)
          if (af.numChannels != numChannels) {
            Console.err.println(s"Warning: DiskIn - channel mismatch (file has ${af.numChannels}, UGen has $numChannels)")
          }
          bufSize     = ctrl.blockSize
          buf         = af.buffer(bufSize)
          gain        = cue.gain
          if (cue.offset > 0L) {
            framesRead = math.min(af.numFrames, cue.offset)
            af.seek(framesRead)
          }
        case Failure(ex) =>
          notifyFail(ex)
      }
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override protected def stopped(): Unit = {
      logStream(s"postStop() $this")
      buf = null
      //      try {
      if (af != null) {
        af.close()
        af = null
      }
      //      } catch {
      //        case NonFatal(ex) =>  // XXX TODO -- what with this?
      //      }
    }

    override def onDownstreamFinish(cause: Throwable): Unit = {
      val all = shape.outlets.forall(out => isClosed(out))
      logStream(s"completeStage() $this - $all")
      if (all) {
        super.onDownstreamFinish(cause)
      } else {
        onPull()
      }
    }

    private def canWrite: Boolean =
      shape.outlets.forall(out => isClosed(out) || isAvailable(out))

    override def onPull(): Unit =
      if (isInitialized && canWrite) {
        process()
      }

    private def process(): Unit = {
      val chunk = math.min(bufSize, af.numFrames - framesRead).toInt
      if (chunk == 0) {
        logStream(s"completeStage() $this")
        completeStage()
      } else {
        af.read(buf, 0, chunk)
        framesRead += chunk
        val _gain = gain
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
              if (_gain != 1.0) Util.mul(b, 0, chunk, _gain)

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