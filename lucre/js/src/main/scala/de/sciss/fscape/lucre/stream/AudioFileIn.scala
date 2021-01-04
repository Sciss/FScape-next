/*
 *  AudioFileIn.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.lucre.stream

import java.net.URI

import akka.stream.Attributes
import akka.stream.stage.OutHandler
import de.sciss.audiofile.AudioFile.Frames
import de.sciss.audiofile.{AsyncAudioFile, AudioFile}
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.shapes.UniformSourceShape
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufD, Control, Layer, OutD}
import de.sciss.fscape.{Util, stream}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future
import scala.util.{Failure, Success}

// asynchronous
object AudioFileIn {
  def apply(uri: URI, offset: Long, gain: Double, numChannels: Int)(implicit b: stream.Builder): Vec[OutD] = {
    val nameL   = Util.mkLogicName(name, uri)
    val source  = new Stage(layer = b.layer, uri = uri, offset = offset, gain = gain,
      numChannels = numChannels, nameL = nameL)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "AudioFileIn"

  private type Shp = UniformSourceShape[BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(layer: Layer, uri: URI, offset: Long, gain: Double, numChannels: Int, nameL: String)
                           (implicit ctrl: Control)
    extends StageImpl[Shp](nameL) {

    val shape: Shape = UniformSourceShape(Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")))

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, uri = uri, offset = offset, gain = gain,
        name = nameL, numChannels = numChannels)
  }

  private final class Logic(shape: Shp, layer: Layer, uri: URI, offset: Long, gain: Double,
                            name: String, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with OutHandler {

    private[this] var afFut   : Future[AsyncAudioFile] = _
    private[this] var af      : AsyncAudioFile  = _

    private[this] var buf     : Frames      = _
    private[this] var bufOuts : Array[BufD] = _
    private[this] var bufSize : Int         = _
    private[this] var bufOff = 0

    private[this] var framesRead    = 0L
    private[this] var _isComplete   = false
    private[this] var afReady       = false

    shape.outlets.foreach(setHandler(_, this))

    override protected def init(): Unit = {
      super.init()
      logStream.info(s"init() $this")
      import ctrl.config.executionContext
      afFut   = AudioFile.openReadAsync(uri)
      bufSize = ctrl.blockSize
      afFut.onComplete { tr =>
        async {
          tr match {
            case Success(_af) =>
              if (_isComplete) {
                _af.close()
              } else {
                af = _af
                if (af.numChannels != numChannels) {
                  Console.err.println(
                    s"Warning: DiskIn - channel mismatch (file has ${af.numChannels}, UGen has $numChannels)")
                }
                val numChMx = math.max(numChannels, af.numChannels)
                bufOuts = new Array[BufD](numChMx)
                buf     = new Array[Array[Double]](numChMx)
                if (offset > 0L) {
                  framesRead = math.min(af.numFrames, offset)
                  af.seek(framesRead)
                  ()
                }
                readChunk()
              }

            case Failure(ex) =>
              if (!_isComplete) notifyFail(ex)
          }
        }
      }
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override protected def stopped(): Unit = {
      logStream.info(s"postStop() $this")
      buf = null
      var ch = 0
      if (bufOuts != null) {
        while (ch < bufOuts.length) {
          val b = bufOuts(ch)
          if (b != null) b.release()
          ch += 1
        }
        bufOuts = null
      }
      _isComplete = true
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
      logStream.info(s"completeStage() $this - $all")
      if (all) {
        super.onDownstreamFinish(cause)
      } else {
        onPull()
      }
    }

    private def canProcess: Boolean =
      afReady && shape.outlets.forall(out => isClosed(out) || isAvailable(out))

    override def onPull(): Unit =
      if (canProcess) process()

    private def readChunk(): Unit = {
      afReady = false
      val chunk = math.min(bufSize, af.numFrames - framesRead).toInt
      if (chunk == 0) {
        logStream.info(s"readChunk() -> completeStage $this")
        completeStage()
      } else {
        var ch = 0
        while (ch < numChannels) {
          val out = shape.out(ch)
          if (!isClosed(out)) {
            val bufOut    = ctrl.borrowBufD()
            bufOuts (ch)  = bufOut
            buf     (ch)  = bufOut.buf
          }
          ch += 1
        }
        val futRead = af.read(buf, 0, chunk)
        import ctrl.config.executionContext
        futRead.onComplete { tr =>
          async {
            tr match {
              case Success(_) =>
                bufOff      = chunk
                framesRead += chunk
                afReady     = true
                if (!_isComplete && canProcess) process()

              case Failure(ex) =>
                if (!_isComplete) notifyFail(ex)
            }
          }
        }
      }
    }

    private def process(): Unit = {
      val chunk = bufOff
      assert (chunk > 0)

      val _gain = gain
      var ch = 0
      while (ch < numChannels) {
        val bufOut      = bufOuts(ch)
        if (bufOut != null) {
          if (_gain != 1.0) Util.mul(bufOut.buf, 0, chunk, _gain)
          bufOut.size   = chunk
          val out       = shape.out(ch)
          push(out, bufOut)
          bufOuts (ch)  = null
          buf     (ch)  = null
        }
        ch += 1
      }

      readChunk()
    }
  }
}