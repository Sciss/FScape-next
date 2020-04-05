/*
 *  ZipWindowN.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, Inlet, Outlet}
import akka.stream.stage.{InHandler, OutHandler}
import de.sciss.fscape.{Util, logStream}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.collection.immutable.{Seq => ISeq}

/** Zips a number of signals into one output based on a window length. */
object ZipWindowN {
  /**
    * @param in         the signals to zip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: ISeq[OutD], size: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(layer = b.layer, numInputs = in.size)
    val stage   = b.add(stage0)
    (in zip stage.inputs).foreach { case (output, input) =>
      b.connect(output, input)
    }
    b.connect(size, stage.size)
    stage.out
  }

  private final val name = "ZipWindowN"

  private final case class Shp(inputs: ISeq[InD], size: InI, out: OutD) extends akka.stream.Shape {
    val inlets : ISeq[Inlet [_]] = inputs :+ size
    val outlets: ISeq[Outlet[_]] = Vector(out)

    override def deepCopy(): Shp =
      Shp(inputs.map(_.carbonCopy()), size.carbonCopy(), out.carbonCopy())
  }

  private final class Stage(layer: Layer, numInputs: Int)(implicit ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = Shp(
      inputs  = Vector.tabulate(numInputs)(idx => InD(s"$name.in$idx")),
      size    = InI (s"$name.size"),
      out     = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) {

    private[this] var bufOut: BufD = _
    private[this] var bufIn1: BufI = _

    private[this] var winRemain         = 0
    private[this] var sizeOff           = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var sizeRemain        = 0
    private[this] var outSent           = true

    private[this] var isNextWindow      = true

    private[this] val inputs: Array[Input]  = shape.inputs.iterator.map(new Input(_)).toArray

    private[this] val numInputs             = inputs.length
    private[this] var inIndex               = numInputs - 1

    private[this] var size = -1 // negative signalizes non-yet-initialized

    @inline
    private[this] def shouldNext  = isNextWindow && (size > 0 || sizeOff < sizeRemain)

    private final class Input(val let: InD) extends InHandler {
      var buf: BufD = _
      var off       = 0
      var remain    = 0
      var sent      = true

      override def toString: String = {
        val sentS   = s"sent = ${  if (sent)             "T" else "f"}"
        val closedS = s"closed = ${if (isClosed(let))    "T" else "f"}"
        val availS  = s"avail = ${ if (isAvailable(let)) "T" else "f"}"
        val flags   = s"$sentS, $closedS, $availS"
        f"Input($buf, off = $off%05d, remain = $remain%05d, $flags)"
      }

      def read(): Unit = {
        // println(s"in.read($let)")
        tryFree()
        buf      = grab(let)
        tryPull(let)
        off      = 0
        remain   = buf.size
      }

      def onPush(): Unit = {
        logStream(s"onPush($let)")
        if (remain == 0) {
          read()
          process()
        }
      }

      def tryFree(): Unit =
        if (buf != null) {
          buf.release()
          buf = null
        }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($let)")
        process()
      }
    }

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
    }

    private def freeInputBuffers(): Unit = {
      inputs.foreach(_.tryFree())
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    private def freeOutputBuffers(): Unit =
      if (bufOut != null) {
        bufOut.release()
        bufOut = null
      }

    private def updateSize(): Unit =
      if (sizeRemain == 0) {
        readSize()
        process()
      }

    private def readSize(): Unit = {
      // println("readSize()")
      if (bufIn1 != null) bufIn1.release()
      bufIn1      = grab(shape.size)
      tryPull(shape.size)
      sizeOff     = 0
      sizeRemain  = bufIn1.size
    }

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    @tailrec
    private def process(): Unit = {
      // logStream(s"process() $this")

      // becomes `true` if state changes,
      // in that case we run this method again.
      var stateChange = false

      if (sizeRemain == 0 && isAvailable(shape.size)) readSize()

      if (shouldNext) {
        inIndex += 1
        if (inIndex == numInputs) inIndex = 0
        if (sizeRemain > 0) {
          size = math.max(1, bufIn1.buf(sizeOff))
        }
        winRemain     = size
        isNextWindow  = false
        stateChange   = true
      }

      val in = inputs(inIndex)
      if (in.remain == 0 && isAvailable(in.let)) in.read()

      val inWinRem = math.min(in.remain, winRemain)
      if (inWinRem > 0) {
        if (outSent) {
          bufOut        = allocOutBuf()
          outRemain     = bufOut.size
          outOff        = 0
          outSent       = false
          stateChange   = true
        }

        val chunk0  = math.min(inWinRem, outRemain)
        val chunk   = if (sizeRemain == 0 && isClosed(shape.size) && !isAvailable(shape.size)) chunk0
        else math.min(chunk0, sizeRemain)

        if (chunk > 0) {
          Util.copy(in.buf.buf, in.off, bufOut.buf, outOff, chunk)
          in.off     += chunk
          in.remain  -= chunk
          outOff     += chunk
          outRemain  -= chunk
          winRemain  -= chunk
          if (sizeRemain > 0) {
            sizeOff    += chunk
            sizeRemain -= chunk
          }
          if (winRemain == 0) {
            isNextWindow = true
          }
          stateChange = true
        }
      }

      val flush = in.remain == 0 && !isNextWindow && isClosed(in.let) && !isAvailable(in.let)
      if (!outSent && (outRemain == 0 || flush) && isAvailable(shape.out)) {
        if (outOff > 0) {
          bufOut.size = outOff
          push(shape.out, bufOut)
        } else {
          bufOut.release()
        }
        bufOut      = null
        outSent     = true
        stateChange = true
      }

      if (flush && outSent) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }

    inputs.foreach(in => setHandler(in.let, in))

    setHandler(shape.size, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush(${shape.size})")
        updateSize()
      }

      override def onUpstreamFinish(): Unit = {
        val valid = isAvailable(shape.size) || size > 0 || sizeRemain > 0
        logStream(s"onUpstreamFinish(${shape.size}); valid = $valid")
        if (valid) {
          process()
        } else {
          super.onUpstreamFinish()
        }
      }
    })

    setHandler(shape.out, new OutHandler {
      def onPull(): Unit = {
        logStream(s"onPull(${shape.out})")
        process()
      }
    })
  }
}