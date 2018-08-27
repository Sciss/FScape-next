/*
 *  ZipWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{StageImpl, NodeImpl}

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.collection.immutable.{Seq => ISeq}

/** Zips two signals into one based on a window length. */
object ZipWindow {
  /**
    * @param a      the first signal to zip
    * @param b      the second signal to zip
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply(a: OutD, b: OutD, size: OutI)(implicit builder: Builder): OutD =
    ZipWindowN(in = Vector(a, b), size = size)
}

/** Zips a number of signals into one output based on a window length. */
object ZipWindowN {
  /**
    * @param in         the signals to zip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: ISeq[OutD], size: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(numInputs = in.size)
    val stage   = b.add(stage0)
    (in zip stage.inputs).foreach { case (output, input) =>
      b.connect(output, input)
    }
    b.connect(size, stage.size)
    stage.out
  }

  private final case class Shape(inputs: ISeq[InD], size: InI, out: OutD) extends akka.stream.Shape {
    val inlets : ISeq[Inlet [_]] = inputs :+ size
    val outlets: ISeq[Outlet[_]] = Vector(out)

    override def deepCopy(): Shape =
      Shape(inputs.map(_.carbonCopy()), size.carbonCopy(), out.carbonCopy())

    override def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): Shape = {
      require(inlets .size == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
      require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
      val init = inlets.init.asInstanceOf[ISeq[Inlet[BufD]]]
      val last = inlets.last.asInstanceOf[Inlet[BufI]]
      Shape(init, last, outlets.head.asInstanceOf[OutD])
    }
  }

  private final class Stage(numInputs: Int)(implicit ctrl: Control) extends StageImpl[Shape]("ZipWindow") {
    val shape = Shape(
      inputs  = Vector.tabulate(numInputs)(idx => InD(s"ZipWindow.in$idx")),
      size    = InI ("ZipWindow.size"),
      out     = OutD("ZipWindow.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl("ZipWindow", shape) {

    private[this] var bufOut: BufD = _
    private[this] var bufIn1: BufI = _

    private[this] var winRemain         = 0
    private[this] var sizeOff           = 0
    private[this] var outOff            = 0  // regarding `bufOut`
    private[this] var outRemain         = 0
    private[this] var sizeRemain        = 0
    private[this] var outSent           = true

    private[this] var isNextWindow      = true

    private[this] val inputs: Array[Input]  = shape.inputs.map(new Input(_))(breakOut)

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

    override def preStart(): Unit =
      shape.inlets.foreach(pull(_))

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
        if (sizeOff < sizeRemain) {
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

      val flush = in.remain == 0 && isClosed(in.let) && !isAvailable(in.let)
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
        logStream(s"onUpstreamFinish(${shape.size})")
        process()
        ()  // keep running
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