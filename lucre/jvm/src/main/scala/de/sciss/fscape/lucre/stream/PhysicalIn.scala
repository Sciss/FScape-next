/*
 *  PhysicalIn.scala
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

package de.sciss.fscape.lucre
package stream

import akka.stream.Attributes
import akka.stream.stage.OutHandler
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.shapes.UniformSourceShape
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufD, Builder, Control, Layer, OutD}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.math.min

object PhysicalIn {
  def apply(numChannels: Int)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(layer = b.layer, numChannels = numChannels)
    val stage   = b.add(source)
    stage.outlets.toIndexedSeq
  }

  private final val name = "PhysicalIn"

  private type Shp = UniformSourceShape[BufD]

  private final class Stage(layer: Layer, numChannels: Int)(implicit protected val ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = UniformSourceShape[BufD](
      Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, numChannels = numChannels)
  }

  private final class Logic(shape: Shp, layer: Layer, numChannels: Int)
                           (implicit ctrl: Control)
    extends NodeImpl[Shp](name, layer, shape)
      with NodeHasInitImpl with OutHandler { logic =>

    private[this] var pulled        = 0

    private[this] val circleSize    = 3
    private[this] val rtBufCircle   = Array.fill(circleSize)(new Array[Array[Float]](numChannels))
    private[this] var readCircle    = 0
    private[this] var writtenCircle = 0
    private[this] var writeCircleRT = 0

    private[this] var numChannelsOpen = numChannels

    private[this] var LAST_REP_PROC   = 0L
    private[this] var LAST_REP_PROC_C = 0
    private[this] var LAST_REP_IN     = 0L
    private[this] var LAST_REP_IN_C   = 0

    private[this] val DEBUG     = false
    private[this] var okRT      = false
    private[this] var _stopped  = false

//    private[this] val realtimeFun: js.Function1[AudioProcessingEvent, Unit] = { e =>
//      if (okRT) { // there is a weird condition in which the realtime function is still called after `stopped`
//        val b         = e.inputBuffer
//        val rtBuf     = rtBufCircle(writeCircleRT % circleSize)
//        val numCh     = min(b.numberOfChannels, rtBuf.length)
//        val newWrite  = writeCircleRT + 1
//        writeCircleRT = newWrite
//
//        var ch = 0
//        while (ch < numCh) {
//          b.copyFromChannel(rtBuf(ch), ch, 0)
//          ch += 1
//        }
//
//        if (DEBUG) {
//          val NOW = System.currentTimeMillis()
//          val DT  = NOW - LAST_REP_PROC
//          if (DT > 1000) {
//            val len  = rtBuf(0).length
//            val thru = ((newWrite - LAST_REP_PROC_C) * len) * 1000.0 / DT
//            println(s"<AudioProcessingEvent> buffers written = $writtenCircle; through-put is $thru Hz")
//            LAST_REP_PROC   = NOW
//            LAST_REP_PROC_C = newWrite
//          }
//        }
//
//        async {
//          writtenCircle = newWrite
//          if (canProcess) process()
//        }
//      }
//    }

    {
      val outs = shape.outlets
      var ch = 0
      while (ch < numChannels) {
        val out = outs(ch)
        setHandler(out, this)
        ch += 1
      }
    }

    def onPull(): Unit = {
      pulled += 1
      //  if (DEBUG) println(s"<$in> onPush; pushed = $pushed")
      if (canProcess) process()
    }

    override def onDownstreamFinish(cause: Throwable): Unit = {
      numChannelsOpen -= 1
      val all = numChannelsOpen == 0
      logStream.info(s"completeStage() $this - $all")
      if (all) {
        super.onDownstreamFinish(cause)
      } else {
        if (canProcess) process()
      }
    }

    // ---- StageLogic

    override protected def init(): Unit = {
      // super.init()
      logStream.info(s"$this - init()")
    }

    override protected def launch(): Unit = {
      super.launch()
      val bufSize = control.blockSize
      var ch = 0
      while (ch < numChannels) {
        var ci = 0
        while (ci < circleSize) {
          rtBufCircle(ci)(ch) = new Array[Float](bufSize)
          ci += 1
        }
        ch += 1
      }
      okRT = true
//      scriptProcessor.onaudioprocess = realtimeFun
//      mediaStream     .connect(scriptProcessor)
//      scriptProcessor .connect(audioContext.destination)
    }

    override protected def stopped(): Unit = {
      logStream.info(s"$this - postStop()")
      okRT      = false
      _stopped  = true

      var ch = 0
      while (ch < numChannels) {
        var ci = 0
        while (ci < circleSize) {
          rtBufCircle(ci)(ch) = null
          ci += 1
        }
        ch += 1
      }
    }

    private def canProcess: Boolean =
      pulled == numChannelsOpen && readCircle < writtenCircle

    private def process(): Unit = {
      logStream.debug(s"process() $this")
      pulled = 0

      val rtBuf   = rtBufCircle(readCircle % circleSize)
      val newRead = readCircle + 1
      readCircle  = newRead

      var ch = 0
      while (ch < numChannels) {
        val out = shape.out(ch)
        if (!isClosed(out)) {
          val bufOut = ctrl.borrowBufD()
          val a     = bufOut.buf
          val b     = rtBuf(ch)
          val chunk = min(bufOut.size, b.length)
          var i = 0
          while (i < chunk) {
            a(i) = b(i).toDouble
            i += 1
          }
          bufOut.size = chunk
          push(shape.out(ch), bufOut)
        }
        ch += 1
      }

      if (DEBUG) {
        val NOW = System.currentTimeMillis()
        val DT  = NOW - LAST_REP_IN
        if (DT > 1000) {
          val len   = rtBuf(0).length
          val thru  = ((newRead - LAST_REP_IN_C) * len) * 1000.0 / DT
          println(s"<process()> buffers read = $readCircle; through-put is $thru Hz")
          LAST_REP_IN   = NOW
          LAST_REP_IN_C = newRead
        }
      }
    }
  }
}