/*
 *  UnzipWindowImpl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, Shape}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.{BufD, BufI, Control}

import scala.collection.breakOut
import scala.collection.immutable.{Seq => ISeq}

case class UnzipWindowShape(in0: Inlet[BufD], in1: Inlet[BufI], outlets: ISeq[Outlet[BufD]]) extends Shape {
  val inlets: ISeq[Inlet[_]] = Vector(in0, in1)

  override def deepCopy(): UnzipWindowShape =
    UnzipWindowShape(in0.carbonCopy(), in1.carbonCopy(), outlets.map(_.carbonCopy()))

  override def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): Shape = {
    require(inlets .size == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    UnzipWindowShape(inlets(0).asInstanceOf[Inlet[BufD]], inlets(1).asInstanceOf[Inlet[BufI]],
      outlets.asInstanceOf[ISeq[Outlet[BufD]]])
  }
}

final class UnzipWindowStageImpl(numOutputs: Int, ctrl: Control) extends GraphStage[UnzipWindowShape] {
  val shape = UnzipWindowShape(
    in0     = Inlet[BufD]("UnzipWindow.in"),
    in1     = Inlet[BufI]("UnzipWindow.size"),
    outlets = Vector.tabulate(numOutputs)(idx => Outlet[BufD](s"UnzipWindow.out$idx"))
  )

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new UnzipWindowLogicImpl(shape, ctrl)
}

final class UnzipWindowLogicImpl(shape: UnzipWindowShape, ctrl: Control) extends GraphStageLogic(shape) {
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

  private final class Output(val let: Outlet[BufD]) extends OutHandler {
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

  private def readIns(): Unit = {
    freeInputBuffers()
    val sh    = shape
    bufIn0    = grab(sh.in0)
    tryPull(sh.in0)

    if (isAvailable(sh.in1)) {
      bufIn1 = grab(sh.in1)
      tryPull(sh.in1)
    }

    canRead = false
  }

  private def freeInputBuffers(): Unit = {
    if (bufIn0 != null) {
      bufIn0.release()(ctrl)
      bufIn0 = null
    }
    if (bufIn1 != null) {
      bufIn1.release()(ctrl)
      bufIn1 = null
    }
  }

  private def freeOutputBuffers(): Unit =
    outputs.foreach { out =>
      if (out.buf != null) {
        out.buf.release()(ctrl)
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
      readIns()
      inRemain    = bufIn0.size
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
          out.buf.release()(ctrl)
        }
        out.buf     = null
        out.sent    = true
        stateChange = true
      }
      idx += 1
    }

    if      (flush && outputs.forall(_.sent)) completeStage()
    else if (stateChange)                     process()
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