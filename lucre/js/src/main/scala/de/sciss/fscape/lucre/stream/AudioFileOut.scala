/*
 *  AudioFileOut.scala
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
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import de.sciss.asyncfile
import de.sciss.audiofile.AudioFile.Frames
import de.sciss.audiofile.{AsyncAudioFile, AudioFile, AudioFileSpec, AudioFileType}
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.Util
import de.sciss.fscape.lucre.graph.{AudioFileOut => AF}
import de.sciss.fscape.stream.impl.shapes.In3UniformFanInShape
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufD, BufI, BufL, Builder, Control, InD, InI, Layer, OutD, OutI, OutL}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

// asynchronous
object AudioFileOut {
  def apply(uri: URI, fileType: OutI, sampleFormat: OutI, sampleRate: OutD, in: ISeq[OutD])
           (implicit b: Builder): OutL = {
    val nameL   = Util.mkLogicName(name, uri)
    val stage0  = new Stage(layer = b.layer, uri = uri, numChannels = in.size, nameL = nameL)
    val stage   = b.add(stage0)
    b.connect(fileType    , stage.in0)
    b.connect(sampleFormat, stage.in1)
    b.connect(sampleRate  , stage.in2)
    (in zip stage.inlets3).foreach { case (output, input) =>
      b.connect(output, input)
    }
    stage.out
  }

  private final val name = "AudioFileOut"

  private type Shp = In3UniformFanInShape[BufI, BufI, BufD, BufD, BufL]

  private final class Stage(layer: Layer, uri: URI, nameL: String, numChannels: Int)
                           (implicit protected val ctrl: Control)
    extends StageImpl[Shp](nameL) {

    val shape: Shape = In3UniformFanInShape(
      InI (s"$name.fileType"    ),
      InI (s"$name.sampleFormat"),
      InD (s"$name.sampleRate"  ),
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch")),
      OutL(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer, uri, name = nameL, numChannels = numChannels)
  }

  private final class Logic(shape: Shp, layer: Layer, uri: URI, name: String, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl
      with OutHandler { logic: GraphStageLogic =>

    // ---- impl ----

    private[this] var afFut   : Future[AsyncAudioFile] = _
    private[this] var af      : AsyncAudioFile  = _

    private[this] var pushed        = 0
    private[this] val bufIns        = new Array[BufD]         (numChannels)
    private[this] val buf: Frames   = new Array[Array[Double]](numChannels)

    private[this] var shouldStop    = false
    private[this] var _isComplete   = false

//    protected def isSuccess     : Boolean  = _isComplete
//    protected def framesWritten : Long     = af.numFrames

    private[this] var fileType      = -1
    private[this] var sampleFormat  = -1
    private[this] var sampleRate    = -1.0
    private[this] var afReady       = false

    override protected def init(): Unit = {
      super.init()
      logStream.info(s"init() $this")
      setKeepGoing(true)
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    private def updateSpec(): Unit = {
      if (fileType >= 0 && sampleFormat >= 0 && sampleRate >= 0 && afFut == null) {
        val spec = AudioFileSpec(AF.fileType(fileType), AF.sampleFormat(sampleFormat),
          numChannels = numChannels, sampleRate = sampleRate)
        import ctrl.config.executionContext
        afFut = AudioFile.openWriteAsync(uri, spec)
        afFut.onComplete { tr =>
          // println(s"$name openWriteAsync() complete: $tr")
          async {
            tr match {
              case Success(_af) =>
                if (_isComplete) {
                  _af.close()
                } else {
                  af      = _af
                  afReady = true
                  if (canProcess) process()
                }

              case Failure(ex) =>
                if (!_isComplete) {
                  notifyFail(ex)
                }
            }
          }
        }
      }
    }

    private def canProcess: Boolean =
      afReady && pushed == numChannels && (isClosed(shape.out) || isAvailable(shape.out))

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        val buf = grab(shape.in0)
        if (buf.size > 0 && fileType < 0) {
          logStream.debug("AudioFileOut: fileType")
          val _fileType = math.min(AF.maxFileTypeId, buf.buf(0))
          fileType = if (_fileType >= 0) _fileType else {
            import asyncfile.Ops._
            val ext   = uri.extL
            val tpe   = AudioFileType.writable.find(_.extensions.contains(ext)).getOrElse(AudioFileType.AIFF)
            AF.id(tpe)
          }
          updateSpec()
          if (canProcess) process()
        }
        buf.release()
      }

      override def onUpstreamFinish(): Unit =
        if (fileType < 0) {
          logStream.info(s"$logic: no fileType - onUpstreamFinish()")
          super.onUpstreamFinish()
        }
    })

    setHandler(shape.in1, new InHandler {
      def onPush(): Unit = {
        val buf = grab(shape.in1)
        if (buf.size > 0 && sampleFormat < 0) {
          logStream.debug("AudioFileOut: sampleFormat")
          sampleFormat = math.max(0, math.min(AF.maxSampleFormatId, buf.buf(0)))
          updateSpec()
          if (canProcess) process()
        }
        buf.release()
      }

      override def onUpstreamFinish(): Unit =
        if (sampleFormat < 0) {
          logStream.info(s"$logic: no sampleFormat - onUpstreamFinish()")
          super.onUpstreamFinish()
        }
    })

    setHandler(shape.in2, new InHandler {
      def onPush(): Unit = {
        val buf = grab(shape.in2)
        if (buf.size > 0 && sampleRate < 0) {
          logStream.debug("AudioFileOut: sampleRate")
          sampleRate = math.max(0.0, buf.buf(0))
          updateSpec()
          if (canProcess) process()
        }
        buf.release()
      }

      override def onUpstreamFinish(): Unit =
        if (sampleRate < 0) {
          logStream.info(s"$logic: no sampleRate - onUpstreamFinish()")
          super.onUpstreamFinish()
        }
    })

    {
      val ins = shape.inlets3
      var ch = 0
      while (ch < numChannels) {
        val in = ins(ch)
        setHandler(in, new InH(in /* , ch */))
        ch += 1
      }
    }
    setHandler(shape.out, this)

    private final class InH(in: InD /* , ch: Int */) extends InHandler {
      def onPush(): Unit = {
        pushed += 1
        if (canProcess) process()
      }

      override def onUpstreamFinish(): Unit = {
        if (isAvailable(in)) {
          logStream.info(s"$logic - onUpstreamFinish($in) - shouldStop")
          shouldStop = true
        } else {
          logStream.info(s"$logic - onUpstreamFinish($in) - isComplete")
          _isComplete = true
          flushAndComplete()
        }
      }
    }

    override protected def stopped(): Unit = {
      logStream.info(s"$this - stopped()")
      releaseBufIns()
      if (af != null) {
        af.close()
        ()
      }
    }

    def onPull(): Unit =
      if (canProcess) process()

    // we do not care if the consumer of the frame information closes early.
    override def onDownstreamFinish(cause: Throwable): Unit = {
      logStream.debug(s"$this - out - onDownstreamFinish()")
      onPull()
    }

    private def flushAndComplete(): Unit = {
      logStream.info(s"$this - flushAndComplete()")
      import ctrl.config.executionContext
      af.close().onComplete { tr =>
        async {
          logStream.debug(s"$this - flushAndComplete() - success? ${tr.isSuccess}")
          tr match {
            case Success(_)   =>
              completeStage()
              // setKeepGoing(false)
              // postStop()
            case Failure(ex)  =>
              notifyFail(ex)
              // setKeepGoing(false)
          }
        }
      }
    }

    private def releaseBufIns(): Unit = {
      var ch = 0
      while (ch < numChannels) {
        val b = bufIns(ch)
        if (b != null) {
          b.release()
          bufIns(ch) = null
          buf   (ch) = null
        }
        ch += 1
      }
    }

    private def process(): Unit = {
      logStream.debug(s"process() $this")
      pushed = 0

      var ch    = 0
      var chunk = 0
      while (ch < numChannels) {
        val bufIn   = grab(shape.inlets3(ch))
        bufIns(ch)  = bufIn
        buf   (ch)  = bufIn.buf
        chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
        ch += 1
      }

      val pos1 = af.position + 1

      try {
        logStream.debug(s"$this - af.write(_, 0, $chunk)")
        val futWrite = af.write(buf, 0, chunk)
        afReady = false
        import ctrl.config.executionContext
        futWrite.onComplete { tr =>
          async {
            logStream.debug(s"$this - futWrite complete")
            releaseBufIns()

            tr match {
              case Success(_) =>
                afReady = true
                if (_isComplete) {
                  flushAndComplete()
                } else {
                  if (canProcess) process()
                }

              case Failure(ex) =>
                /*if (!_isComplete)*/ notifyFail(ex)
            }
          }
        }

      } catch {
        case NonFatal(ex) =>
          notifyFail(ex)
      }

      // XXX TODO this should really happen after `futWrite` succeeds
      val bufOut  = control.borrowBufL()
      val arrOut  = bufOut.buf
      var j = 0
      while (j < chunk) {
        arrOut(j) = pos1 + j
        j += 1
      }
      bufOut.size = chunk
      if (!isClosed(shape.out)) push(shape.out, bufOut)

      if (shouldStop) {
        _isComplete = true
        if (afReady) {
          flushAndComplete()
        }
      } else {
        ch = 0
        while (ch < numChannels) {
          pull(shape.inlets3(ch))
          ch += 1
        }
      }
    }
  }
}