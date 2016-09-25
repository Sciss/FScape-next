/*
 *  Slices.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeImpl}

import scala.annotation.tailrec

// XXX TODO --- we could support other types than Double
object Slices {
  def apply(in: OutD, spans: OutL)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in   , stage.in0)
    b.connect(spans, stage.in1)
    stage.out
  }

  private final val name = "Slices"

  private type Shape = FanInShape2[BufD, BufL, BufD]

  private final class Stage(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](name) {

    val shape = new FanInShape2(
      in0 = InD (s"$name.in"   ),
      in1 = InL (s"$name.spans"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape) with OutHandler {

    private[this] var af: FileBuffer  = _
    private[this] val bufSize       = ctrl.blockSize
    private[this] var buf           = new Array[Double](bufSize)

    private[this] var framesWritten = 0L
    private[this] var framesRead    = 0L

    setHandler(shape.out, this)
    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = onPush0()

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish(${shape.in0}); read = $framesRead; written = $framesWritten")
        if (isAvailable(shape.out)) onPull()
//        super.onUpstreamFinish()
      }
    })
    setHandler(shape.in1, new InHandler {
      def onPush(): Unit = process()

      override def onUpstreamFinish(): Unit = {
        ??? // super.onUpstreamFinish()
      }
    })

    override def preStart(): Unit = {
      af = FileBuffer()
      pull(shape.in0)
      pull(shape.in1)
    }

    override protected def stopped(): Unit = {
      super.stopped()
      buf = null
      af.dispose()
      af = null
    }

    private[this] var spansRemain = 0
    private[this] var spansOff    = 0
    private[this] var bufIn1: BufL = _

    private[this] var spanStart   = 0L
    private[this] var spanStop    = 0L

    private def freeAuxBuffers(): Unit = {
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    @inline
    private[this] def canReadSpans      = spansRemain == 0 && isAvailable(shape.in1)

    @inline
    private[this] def canStartNextSpan  = spansRemain > 0 && framesWritten == spanStop

    @inline
    private[this] def canAllocOut       = ??? : Boolean

    @tailrec
    private def process(): Unit = {
      var stateChanged = false

      if (canReadSpans) {
        freeAuxBuffers()
        bufIn1        = grab(shape.in1)
        val sz        = bufIn1.size
        spansRemain   = sz - (sz % 2)
        spansOff      = 0
        stateChanged  = true
      }

      if (canStartNextSpan) {
        spanStart     = bufIn1.buf(spansOff)
        spanStop      = bufIn1.buf(spansOff + 1)
        spansOff     += 2
        spansRemain  -= 2
        framesWritten = spanStart
        stateChanged  = true
      }

      if (canAllocOut) {
        ???
      }

      if (stateChanged) process()
    }

    private def onPush0(): Unit = {
      val bufIn = grab(shape.in0)
      tryPull(shape.in0)
      val chunk = bufIn.size
      logStream(s"onPush(${shape.in0}) $chunk; read = $framesRead; written = $framesWritten")

      try {
        if (af.position != framesWritten) af.position = framesWritten
        af.write(bufIn.buf, 0, chunk)
        framesWritten += chunk
        // logStream(s"framesWritten = $framesWritten")
      } finally {
        bufIn.release()
      }

      if (isAvailable(shape.out)) onPull()
    }

    def onPull(): Unit = {
      val chunk = math.min(bufSize, framesWritten - framesRead).toInt
      logStream(s"onPull(${shape.out}) $chunk; read = $framesRead; written = $framesWritten")
      if (chunk == 0) {
        if (isClosed(shape.in0) && !isAvailable(shape.in0)) {
          logStream(s"completeStage() $this")
          completeStage()
        }

      } else {
        if (af.position != framesRead) af.position = framesRead
        val bufOut = ctrl.borrowBufD()
        af.read(bufOut.buf, 0, chunk)
        framesRead += chunk
        bufOut.size = chunk
        push(shape.out, bufOut)
      }
    }
  }
}