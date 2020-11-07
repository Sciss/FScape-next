/*
 *  CdpModifyRadicalReverse.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import de.sciss.audiofile.AudioFile.Frames
import de.sciss.audiofile.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}
import de.sciss.file._
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl}

import scala.util.control.NonFatal

object CdpModifyRadicalReverse {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val source  = new Stage(b.layer)
    val stage   = b.add(source)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "CdpModifyRadicalReverse"

  private type Shp = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control)
    extends BlockingGraphStage[Shp](name) {

    val shape: Shape = FlowShape(
      InD (s"$name.in"),
      OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with InHandler with OutHandler {

    private[this] var afSource      : AudioFile     = _
    private[this] var afSink        : AudioFile     = _
    private[this] var buf           : Frames        = _
    private[this] var bufSize       : Int           = _

    private[this] var tmpFileSource : File          = _
    private[this] var tmpFileSink   : File          = _

    private[this] var framesRead    = 0L
//    private[this] var framesWritten = 0L
    private[this] var state         = 0   // 0 -- write to sink, 1 -- read from source

    setHandler(shape.in , this)
    setHandler(shape.out, this)

    private[this] val cmd = {
      val dir     = sys.env.getOrElse("CDP_BIN_PATH"  , sys.error(s"Environment variable CDP_BIN_PATH not set"))
      val prefix  = sys.env.getOrElse("CDP_BIN_PREFIX", "")
      file(dir) / s"${prefix}modify"
    }

    override def init(): Unit = {
      super.init()
      logStream.info(s"init() $this")
      val tmpFileSource0  = control.createTempFile()
      val tmpFileSink0    = control.createTempFile()
      tmpFileSource       = tmpFileSource0.replaceExt("aif")
      tmpFileSink         = tmpFileSink0  .replaceExt("aif")
      tmpFileSink0  .renameTo(tmpFileSink  ) // CDP parses extensions :(
//      tmpFileSource0.renameTo(tmpFileSource) // CDP parses extensions :(
      // N.B. CDP cannot write to existing output file even if its empty!
      tmpFileSink0.delete()

      // note: sampling-rate is irrelevant for the process
      val specOut = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, numChannels = 1, sampleRate = 44100)
      afSink      = AudioFile.openWrite(tmpFileSink, specOut)
      bufSize     = ctrl.blockSize
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override def onUpstreamFinish(): Unit = {
      proceedToCdp()
//      if (isAvailable(shape.in)) {
//        // shouldStop = true
//      } else {
//        logStream(s"onUpstreamFinish(${shape.in})")
//        // _isSuccess = true
//        super.onUpstreamFinish()
//      }
    }

    override protected def stopped(): Unit = {
      logStream.info(s"postStop() $this")
      buf = null
      if (afSource != null) {
        if (afSource.isOpen) afSource.close()
        afSource = null
      }
      if (afSink != null) {
        if (afSink.isOpen) afSink.close()
        afSink = null
      }
      if (tmpFileSource != null) {
        tmpFileSource.delete()
        tmpFileSource = null
      }
      if (tmpFileSink != null) {
        tmpFileSink.delete()
        tmpFileSink = null
      }
    }

    override def onPush(): Unit =
      if (state == 0) processSink()

    override def onPull(): Unit =
      if (/*isInitialized &&*/ state == 1 && isAvailable(shape.out)) processSource()

    private def checkBuf(chunk: Int): Unit =
      if (buf == null || buf(0).length < chunk) buf = afSink.buffer(chunk)

    private def processSink(): Unit = {
      logStream.debug(s"processSink() $this")

      val bufIn = grab(shape.in)
      val chunk = bufIn.size
      checkBuf(chunk)

      var i = 0
      val a = bufIn.buf
      val b = buf(0)
      while (i < chunk) {
        b(i) = a(i).toFloat
        i += 1
      }

      try {
        afSink.write(buf, 0, chunk)
      } catch {
        case NonFatal(ex) => failStage(ex)
      } finally {
        bufIn.release()
      }

      if (isClosed(shape.in) /* shouldStop */) {
        proceedToCdp()
      } else {
        pull(shape.in)
      }
    }

    private def proceedToCdp(): Unit = {
      // _isSuccess = true
      // completeStage()
      afSink.close()
      processCdp()
      try {
        afSource  = AudioFile.openRead(tmpFileSource)
        state     = 1
      } catch {
        case NonFatal(ex) => failStage(ex)
      }
      onPull()
    }

    private def processCdp(): Unit = {
      val cmdArgs = Seq(cmd.path, "radical", "1", tmpFileSink.path, tmpFileSource.path)
      logStream.debug(s"$this: ${cmdArgs.mkString(" ")}")
      import sys.process._
      val swallow = ProcessLogger(_ => (), Console.err.println)
      val res     = cmdArgs.!(swallow)
      if (res != 0) failStage(new Exception(s"CDP failed with exit code $res"))
      tmpFileSink.delete()
      ()
    }

    private def processSource(): Unit = {
      val chunk = math.min(bufSize, afSource.numFrames - framesRead).toInt
      if (chunk == 0) {
        logStream.debug(s"completeStage() $this")
        completeStage()
      } else {
        checkBuf(chunk)
        afSource.read(buf, 0, chunk)
        framesRead += chunk

        val bufOut  = ctrl.borrowBufD()
        val b       = bufOut.buf
        val a       = buf(0)
        var i       = 0
        while (i < chunk) {
          b(i) = a(i).toDouble
          i += 1
        }
        bufOut.size = chunk
        push(shape.out, bufOut)
      }
    }
  }
}