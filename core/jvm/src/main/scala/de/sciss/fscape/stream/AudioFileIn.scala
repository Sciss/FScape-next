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

package de.sciss.fscape
package stream

import java.io.File
import java.net.URI

import akka.stream.Attributes
import akka.stream.stage.OutHandler
import de.sciss.audiofile.AudioFile
import de.sciss.audiofile.AudioFile.Frames
import de.sciss.fscape.stream.impl.shapes.UniformSourceShape
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl}

import scala.collection.immutable.{IndexedSeq => Vec}

object AudioFileIn {
  def apply(uri: URI, numChannels: Int)(implicit b: Builder): Vec[OutD] = {
    val nameL   = Util.mkLogicName(name, uri)
    val source  = new Stage(layer = b.layer, uri = uri, numChannels = numChannels, nameL = nameL)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "AudioFileIn"

  private type Shp = UniformSourceShape[BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(layer: Layer, uri: URI, numChannels: Int, nameL: String)(implicit ctrl: Control)
    extends BlockingGraphStage[Shp](nameL) {

    val shape: Shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")))

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(nameL, shape, layer = layer, uri = uri, numChannels = numChannels)
  }

  private final class Logic(name: String, shape: Shp, layer: Layer, uri: URI, numChannels: Int)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with OutHandler {

    private[this] var af        : AudioFile  = _
    private[this] var buf       : Frames     = _
    private[this] var bufSize   : Int        = _

    private[this] var framesRead  = 0L

    shape.outlets.foreach(setHandler(_, this))

    override protected def init(): Unit = {
      super.init()
      logStream(s"init() $this")
      val f = new File(uri)
      af    = AudioFile.openRead(f)
      if (af.numChannels != numChannels) {
        Console.err.println(s"Warning: DiskIn - channel mismatch (file has ${af.numChannels}, UGen has $numChannels)")
      }
      bufSize     = ctrl.blockSize
      buf         = af.buffer(bufSize)
    }

    override protected def launch(): Unit = {
      super.launch()
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