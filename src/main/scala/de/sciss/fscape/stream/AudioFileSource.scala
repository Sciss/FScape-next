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

import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.Attributes._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{ActorAttributes, Attributes, Outlet, SourceShape, Supervision}
import de.sciss.file._
import de.sciss.synth.io

import scala.annotation.tailrec
import scala.util.control.NonFatal

// similar to internal `UnfoldResourceSource`
final class AudioFileSource(f: File) extends GraphStage[SourceShape[Double]] { source =>
  val out = Outlet[Double]("AudioFileSource.out")
  
  override val shape = SourceShape(out)
  // override def initialAttributes: Attributes = DefaultAttributes.unfoldResourceSource
  
//  private[this] val IODispatcher = ActorAttributes.Dispatcher("akka.stream.default-blocking-io-dispatcher")
//  override def initialAttributes: Attributes = name("unfoldResourceSource") and IODispatcher

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    private[this] lazy val decider = inheritedAttributes.get[SupervisionStrategy].map(_.decider)
      .getOrElse(Supervision.stoppingDecider)
    
    private[this] var af: io.AudioFile = _
    private[this] var buf: io.Frames = _
    private[this] final val bufSize = 8192
    private[this] var bufOff: Int = _
    private[this] var bufLen: Int = _
    private[this] var framesRead: Long = _

    setHandler(out, this)

    override def preStart(): Unit = {
      println(s"${new java.util.Date()} $source - preStart()")
      af          = io.AudioFile.openRead(f)
      buf         = af.buffer(bufSize)
      bufOff      = 0
      bufLen      = 0
      framesRead  = 0L
    }
    
    @tailrec
    final override def onPull(): Unit = {
      // println("onPull")
      var resumingMode = false
      try {
        val bufEmpty = bufOff == bufLen
        if (bufEmpty && framesRead == af.numFrames) closeStage()
        else {
          if (bufEmpty) {
            val chunkLen = math.min(bufSize, af.numFrames - framesRead).toInt
            // println(s"$source - read - framesRead = $framesRead")
            af.read(buf, 0, chunkLen)
            framesRead += chunkLen
            bufLen = chunkLen
            bufOff = 0
          }
          push(out, buf(0)(bufOff).toDouble) // XXX TODO --- how to handle channels
          bufOff += 1
        }
      } catch {
        case NonFatal(ex) => decider(ex) match {
          case Supervision.Stop =>
            af.close()
            failStage(ex)
          case Supervision.Restart =>
            restartState()
            resumingMode = true
          case Supervision.Resume =>
            resumingMode = true
        }
      }
      if (resumingMode) onPull()
    }

    override def onDownstreamFinish(): Unit = {
      println(s"${new java.util.Date()} $source - onDownstreamFinish()")
      closeStage()
    }

    private def restartState(): Unit = {
      af.close()
      preStart()
    }

    private def closeStage(): Unit = {
      println(s"${new java.util.Date()} $source - closeStage()")
      try {
        af.close()
        completeStage()
      } catch {
        case NonFatal(ex) => failStage(ex)
      }
    }
  }
  override def toString = s"AudioFileSource(${f.name})"
}