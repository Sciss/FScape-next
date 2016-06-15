/*
 *  UnzipWindow.scala
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

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{StageImpl, StageLogicImpl}

import scala.collection.breakOut
import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

/** Unzips a signal into two based on a window length. */
object UnzipWindow {
  /**
    * @param in     the signal to unzip
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply(in: OutD, size: OutI)(implicit b: Builder): (OutD, OutD) = {
    val Seq(out0, out1) = UnzipWindowN(2, in = in, size = size)
    (out0, out1)
  }
}

/** Unzips a signal into a given number of outputs based on a window length. */
object UnzipWindowN {
  /**
    * @param numOutputs the number of outputs to de-interleave the input into
    * @param in         the signal to unzip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply(numOutputs: Int, in: OutD, size: OutI)(implicit b: Builder): Vec[OutD] = {
    val stage0  = new Stage(numOutputs = numOutputs)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)

    stage.outlets.toIndexedSeq
  }

  private final val name = "UnzipWindowN"

  private final case class Shape(in0: InD, in1: InI, outlets: ISeq[OutD]) extends akka.stream.Shape {
    val inlets: ISeq[Inlet[_]] = Vector(in0, in1)

    override def deepCopy(): Shape =
      Shape(in0.carbonCopy(), in1.carbonCopy(), outlets.map(_.carbonCopy()))

    override def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): Shape = {
      require(inlets .size == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
      require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
      Shape(inlets(0).asInstanceOf[Inlet[BufD]], inlets(1).asInstanceOf[Inlet[BufI]],
        outlets.asInstanceOf[ISeq[OutD]])
    }
  }

  private final class Stage(numOutputs: Int)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = Shape(
      in0     = InD(s"$name.in"),
      in1     = InI(s"$name.size"),
      outlets = Vector.tabulate(numOutputs)(idx => OutD(s"$name.out$idx"))
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape) {

    private[this] var bufIn0: BufD = _
    private[this] var bufIn1: BufI = _

    private[this] var canRead = false

    private[this] var winRemain         = 0
    private[this] var inOff             = 0  // regarding `bufIn`
    private[this] var inRemain          = 0

    private[this] var isNextWindow      = true

    /*
        We maintain buffers for each outlet.
        This way we can circulate fast and
        many times per outlet before having
        to flash a particular outlet
        (imagine the case of winSize == 1)
     */
    private[this] val outputs: Array[Output]  = shape.outlets.map(new Output(_))(breakOut)
    private[this] val numOutputs              = outputs.length
    private[this] var outIndex                = numOutputs - 1

    private[this] var size      : Int = _

    @inline
    private[this] def shouldRead  = inRemain  == 0 && canRead
    @inline
    private[this] def shouldNext  = isNextWindow && bufIn0 != null

    private final class Output(val let: OutD) extends OutHandler {
      var buf: BufD = _
      var off       = 0
      var remain    = 0
      var sent      = true

      def onPull(): Unit = process()
    }

    override def preStart(): Unit = {
      val sh = shape
      pull(sh.in0)
      pull(sh.in1)
    }

    override def postStop(): Unit = {
      freeInputBuffers()
      freeOutputBuffers()
    }

    private def readIns(): Int = {
      freeInputBuffers()
      val sh    = shape
      bufIn0    = grab(sh.in0)
      tryPull(sh.in0)

      if (isAvailable(sh.in1)) {
        bufIn1 = grab(sh.in1)
        tryPull(sh.in1)
      }

      canRead = false
      bufIn0.size
    }

    private def freeInputBuffers(): Unit = {
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    private def freeOutputBuffers(): Unit =
      outputs.foreach { out =>
        if (out.buf != null) {
          out.buf.release()
          out.buf = null
        }
      }

    private def updateCanRead(): Unit = {
      val sh = shape
      canRead = isAvailable(shape.in0) &&
        (isClosed(sh.in1) || isAvailable(sh.in1))
      if (canRead) process()
    }

    @inline
    private[this] def allocOutBuf(): BufD = ctrl.borrowBufD()

    private def process(): Unit = {
      // becomes `true` if state changes,
      // in that case we run this method again.
      var stateChange = false

      if (shouldRead) {
        inRemain    = readIns()
        inOff       = 0
        stateChange = true
      }

      if (shouldNext) {
        if (bufIn1 != null && inOff < bufIn1.size) {
          size = math.max(1, bufIn1.buf(inOff))
        }
        winRemain     = size
        outIndex     += 1
        if (outIndex == numOutputs) outIndex = 0
        isNextWindow  = false
        stateChange   = true
      }

      val inWinRem = math.min(inRemain, winRemain)
      if (inWinRem > 0) {
        val out = outputs(outIndex)
        if (out.sent) {
          out.buf       = allocOutBuf()
          out.remain    = out.buf.size
          out.off       = 0
          out.sent      = false
          stateChange   = true
        }

        val chunk = math.min(inWinRem, out.remain)
        if (chunk > 0) {
          Util.copy(bufIn0.buf, inOff, out.buf.buf, out.off, chunk)
          inOff      += chunk
          inRemain   -= chunk
          out.off    += chunk
          out.remain -= chunk
          winRemain  -= chunk
          if (winRemain == 0) {
            isNextWindow = true
          }
          stateChange = true
        }
      }

      val flush = inRemain == 0 && isClosed(shape.in0)
      var idx = 0
      while (idx < numOutputs) {
        val out = outputs(idx)
        if (!out.sent && (out.remain == 0 || flush) && isAvailable(out.let)) {
          if (out.off > 0) {
            out.buf.size = out.off
            push(out.let, out.buf)
          } else {
            out.buf.release()
          }
          out.buf     = null
          out.sent    = true
          stateChange = true
        }
        idx += 1
      }

      if (flush && outputs.forall(_.sent)) {
        logStream(s"$this.completeStage()")
        completeStage()
      }
      else if (stateChange) process()
    }

    setHandler(shape.in0, new InHandler {
      def onPush(): Unit = updateCanRead()

      override def onUpstreamFinish(): Unit = process() // may lead to `flushOut`
    })

    setHandler(shape.in1, new InHandler {
      def onPush(): Unit = updateCanRead()

      override def onUpstreamFinish(): Unit = ()  // keep running
    })

    outputs.foreach(out => setHandler(out.let, out))
  }
}