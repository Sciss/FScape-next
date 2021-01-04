/*
 *  Slices.scala
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

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.annotation.tailrec

object Slices {
  def apply[A, E <: BufElem[A]](in: Outlet[E], spans: OutL)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in   , stage.in0)
    b.connect(spans, stage.in1)
    stage.out
  }

  private final val name = "Slices"

  private type Shp[E] = FanInShape2[E, BufL, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends BlockingGraphStage[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E] (s"$name.in"   ),
      in1 = InL       (s"$name.spans"),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with OutHandler {

    private[this] var af: FileBuffer[A]  = _

    private[this] var framesRead    = 0L  // read from file-buffer and output
    private[this] var framesWritten = 0L  // input and written to file-buffer

    setHandler(shape.out, this)
    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = onPush0()

      override def onUpstreamFinish(): Unit = {
        logStream.info(s"onUpstreamFinish(${shape.in0}); read = $framesWritten; written = $framesRead")
        if (!isAvailable(shape.in0)) {
          clipSpan()
          process()
        }
      }
    })
    setHandler(shape.in1, new InHandler {
      def onPush(): Unit = {
        logStream.debug(s"onPush(${shape.in1})")
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream.info(s"onUpstreamFinish(${shape.in1})")
        process()
      }
    })

    override protected def init(): Unit = {
      super.init()
      af = FileBuffer[A, E]()
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override protected def stopped(): Unit = {
      super.stopped()
      freeAuxBuffers()
      freeOutputBuffer()
      if (af != null) {
        af.dispose()
        af = null
      }
    }

    private[this] var bufIn1: BufL = _
    private[this] var spansOff    = 0
    private[this] var spansRemain = 0

    private[this] var bufOut: E = _
    private[this] var outOff      = 0
    private[this] var outRemain   = 0

    private[this] var spanStart   = 0L
    private[this] var spanStop    = 0L

    private def freeAuxBuffers(): Unit = {
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    private def freeOutputBuffer(): Unit = {
      if (bufOut != null) {
        bufOut.release()
        bufOut = null.asInstanceOf[E]
      }
    }

    @inline
    private[this] def canReadSpans      = spansRemain == 0 && isAvailable(shape.in1)

    @inline
    private[this] def canStartNextSpan  = spansRemain > 0 && framesRead == spanStop

    @inline
    private[this] def canFillOutBuf     = framesRead <= framesWritten

    private def clipSpan(): Unit = {
      spanStart = math.min(spanStart, framesWritten)
      spanStop  = math.min(spanStop , framesWritten)
    }

    @tailrec
    private def process(): Unit = {
      var stateChanged = false

      if (canReadSpans) {
        freeAuxBuffers()
        bufIn1        = grab(shape.in1)
        val sz        = bufIn1.size
        spansRemain   = sz - (sz % 2)
        spansOff      = 0
        tryPull(shape.in1)
        stateChanged  = true
      }

      if (canStartNextSpan) {
        spanStart     = bufIn1.buf(spansOff)
        spanStop      = bufIn1.buf(spansOff + 1)
        if (isClosed(shape.in0) && !isAvailable(shape.in0)) clipSpan()
        spansOff     += 2
        spansRemain  -= 2
        framesRead    = spanStart
        stateChanged  = true
      }

      if (canFillOutBuf) {
        if (bufOut == null) {
          bufOut        = tpe.allocBuf() // ctrl.borrowBufD()
          // we set `outOff` to zero now after writing to out
          // outOff        = 0
          outRemain     = bufOut.size
          stateChanged  = true
        }

        val stop  = math.min(framesWritten, spanStop)
        val chunk = math.min(math.abs(stop - framesRead), outRemain).toInt
        if (chunk > 0) {
          if (stop > framesRead) { // forward
            if (af.position != framesRead) af.position = framesRead
            af.read(bufOut.buf, outOff, chunk)
            framesRead += chunk
          } else {  // backward
            val pos0 = framesRead - chunk
            if (af.position != pos0) af.position = pos0
            af.read(bufOut.buf, outOff, chunk)
            tpe.reverse(bufOut.buf, outOff, chunk)
            framesRead -= chunk
          }
          outOff      += chunk
          outRemain   -= chunk
          stateChanged = true
        }
      }

      if (isAvailable(shape.out)) {
        val flush = framesRead == spanStop && spansRemain == 0 && isClosed(shape.in1) && !isAvailable(shape.in1)
        if (flush || (outRemain == 0 && outOff > 0)) {
          if (outOff > 0) {
            bufOut.size = outOff
            push(shape.out, bufOut)
            bufOut = null.asInstanceOf[E]
          } else {
            freeOutputBuffer()
          }
          outOff = 0
          if (flush) {
            stateChanged = false
            completeStage()
          } else {
            stateChanged = true
          }
        }
      }

      if (stateChanged) process()
    }

    private def onPush0(): Unit = {
      val bufIn = grab(shape.in0)
      tryPull(shape.in0)
      val chunk = bufIn.size
      logStream.debug(s"onPush(${shape.in0}) $chunk; read = $framesWritten; written = $framesRead")

      try {
        if (af.position != framesWritten) af.position = framesWritten
        af.write(bufIn.buf, 0, chunk)
        framesWritten += chunk
        // logStream(s"framesWritten = $framesWritten")
      } finally {
        bufIn.release()
      }

      process()
    }

    def onPull(): Unit = {
      val ok = isInitialized && isAvailable(shape.out)
      logStream.debug(s"onPull(${shape.out}) - $ok")
      if (ok) process()
    }
  }
}