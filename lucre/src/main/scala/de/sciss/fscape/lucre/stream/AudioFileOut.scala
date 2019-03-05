/*
 *  AudioFileOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.lucre.stream

import akka.stream.Attributes
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import de.sciss.file._
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.impl.{BlockingGraphStage, In3UniformFanInShape, NodeImpl}
import de.sciss.fscape.stream.{BufD, BufI, BufL, Builder, Control, InD, InI, OutD, OutI, OutL}
import de.sciss.synth.io
import de.sciss.fscape.lucre.graph.{AudioFileOut => AF}

import scala.collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal

object AudioFileOut {
  def apply(file: File, fileType: OutI, sampleFormat: OutI, sampleRate: OutD, in: ISeq[OutD])
           (implicit b: Builder): OutL = {
    val stage0  = new Stage(file, numChannels = in.size)
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

  private type Shape = In3UniformFanInShape[BufI, BufI, BufD, BufD, BufL]

  private final class Stage(f: File, numChannels: Int)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape: Shape = In3UniformFanInShape(
      InI (s"$name.fileType"    ),
      InI (s"$name.sampleFormat"),
      InD (s"$name.sampleRate"  ),
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch")),
      OutL(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, f, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, file: File, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl(s"$name(${file.name})", shape)
      with OutHandler { logic: GraphStageLogic =>

    // ---- impl ----

    private[this] var af      : io.AudioFile = _
    private[this] var buf     : io.Frames = _

    private[this] var pushed        = 0
    private[this] val bufIns        = new Array[BufD](numChannels)

    private[this] var shouldStop    = false
    private[this] var _isSuccess    = false

    protected def isSuccess     : Boolean  = _isSuccess
    protected def framesWritten : Long     = af.numFrames

    private[this] var fileType      = -1
    private[this] var sampleFormat  = -1
    private[this] var sampleRate    = -1.0
    private[this] var afValid       = false

    private def updateSpec(): Unit = {
      if (fileType >= 0 && sampleFormat >= 0 && sampleRate >= 0) {
        val spec  = io.AudioFileSpec(AF.fileType(fileType), AF.sampleFormat(sampleFormat),
          numChannels = numChannels, sampleRate = sampleRate)
        af        = io.AudioFile.openWrite(file, spec)
        afValid   = true
      }
    }

    private def canProcess: Boolean =
      afValid && pushed == numChannels && isAvailable(shape.out)

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = {
        val buf = grab(shape.in0)
        if (buf.size > 0 && fileType < 0) {
          logStream("AudioFileOut: fileType")
          fileType = math.max(0, math.min(4, buf.buf(0)))
          updateSpec()
          if (canProcess) process()
        }
        buf.release()
      }

      override def onUpstreamFinish(): Unit =
        if (fileType < 0) {
          logStream(s"onUpstreamFinish(${shape.in0})")
          super.onUpstreamFinish()
        }
    })

    setHandler(shape.in1, new InHandler {
      def onPush(): Unit = {
        val buf = grab(shape.in1)
        if (buf.size > 0 && sampleFormat < 0) {
          logStream("AudioFileOut: sampleFormat")
          sampleFormat = math.max(0, math.min(6, buf.buf(0)))
          updateSpec()
          if (canProcess) process()
        }
        buf.release()
      }

      override def onUpstreamFinish(): Unit =
        if (sampleFormat < 0) {
          logStream(s"onUpstreamFinish(${shape.in1})")
          super.onUpstreamFinish()
        }
    })

    setHandler(shape.in2, new InHandler {
      def onPush(): Unit = {
        val buf = grab(shape.in2)
        if (buf.size > 0 && sampleRate < 0) {
          logStream("AudioFileOut: sampleRate")
          sampleRate = math.max(0.0, buf.buf(0))
          updateSpec()
          if (canProcess) process()
        }
        buf.release()
      }

      override def onUpstreamFinish(): Unit =
        if (sampleRate < 0) {
          logStream(s"onUpstreamFinish(${shape.in2})")
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
          shouldStop = true
        } else {
          logStream(s"onUpstreamFinish($in)")
          _isSuccess = true
          super.onUpstreamFinish()
        }
      }
    }

    override def preStart(): Unit = {
      logStream(s"$this - preStart()")
      shape.inlets.foreach(pull(_))
    }

    override protected def stopped(): Unit = {
      logStream(s"$this - postStop()")
      buf = null
      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        ch += 1
      }
      // try {
      if (af != null) af.close()
      // resultP.trySuccess(af.numFrames)
      // } catch {
      //   case NonFatal(ex) => resultP.tryFailure(ex)
      // }
    }

    def onPull(): Unit =
      if (canProcess) process()

    private def process(): Unit = {
      //      logStream(s"process() $this")
      logStream(s"process() $this")
      pushed = 0

      var ch = 0
      var chunk = 0
      while (ch < numChannels) {
        val bufIn = grab(shape.inlets3(ch))
        bufIns(ch)  = bufIn
        chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
        ch += 1
      }

      if (buf == null || buf(0).length < chunk) {
        buf = af.buffer(chunk)
      }

      val pos1 = af.position + 1

      ch = 0
      while (ch < numChannels) {
        var i = 0
        val a = bufIns(ch).buf
        val b = buf(ch)
        while (i < chunk) {
          b(i) = a(i).toFloat
          i += 1
        }
        ch += 1
      }
      try {
        af.write(buf, 0, chunk)
      } catch {
        case NonFatal(ex) =>
          //          resultP.failure(ex)
          failStage(ex)
      } finally {
        ch = 0
        while (ch < numChannels) {
          bufIns(ch).release()
          ch += 1
        }
      }

      val bufOut  = control.borrowBufL()
      val arrOut  = bufOut.buf
      var j = 0
      while (j < chunk) {
        arrOut(j) = pos1 + j
        j += 1
      }
      bufOut.size = chunk
      push(shape.out, bufOut)

      if (shouldStop) {
        _isSuccess = true
        completeStage()
      } else {
        ch = 0
        while (ch < numChannels) {
          pull(shape.inlets3(ch))
          ch += 1
        }
      }
    }

    //    override def onUpstreamFailure(ex: Throwable): Unit = {
    //      resultP.failure(ex)
    //      super.onUpstreamFailure(ex)
    //    }
  }
}