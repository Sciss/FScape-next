/*
 *  ZipWindowImpl.scala
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
package impl

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, Shape}
import de.sciss.fscape.Util

import scala.collection.breakOut
import scala.collection.immutable.{Seq => ISeq}

case class ZipWindowShape(inputs: ISeq[InD], size: InI, out: OutD) extends Shape {
  val inlets : ISeq[Inlet [_]] = inputs :+ size
  val outlets: ISeq[Outlet[_]] = Vector(out)

  override def deepCopy(): ZipWindowShape =
    ZipWindowShape(inputs.map(_.carbonCopy()), size.carbonCopy(), out.carbonCopy())

  override def copyFromPorts(inlets: ISeq[Inlet[_]], outlets: ISeq[Outlet[_]]): Shape = {
    require(inlets .size == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    val init = inlets.init.asInstanceOf[ISeq[Inlet[BufD]]]
    val last = inlets.last.asInstanceOf[Inlet[BufI]]
    ZipWindowShape(init, last, outlets.head.asInstanceOf[OutD])
  }
}

final class ZipWindowStageImpl(numInputs: Int)(implicit ctrl: Control)
  extends GraphStage[ZipWindowShape] {

  val shape = ZipWindowShape(
    inputs  = Vector.tabulate(numInputs)(idx => InD(s"ZipWindow.in$idx")),
    size    = InI ("ZipWindow.size"),
    out     = OutD("ZipWindow.out" )
  )

  def createLogic(attr: Attributes): GraphStageLogic = new ZipWindowLogicImpl(shape)
}

final class ZipWindowLogicImpl(shape: ZipWindowShape)(implicit ctrl: Control)
  extends StageLogicImpl("ZipWindow", shape) {

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

    override def toString = {
      val sentS   = s"sent = ${if (sent) "T" else "f"}"
      val closedS = s"closed = ${if (isClosed(let)) "T" else "f"}"
      val availS  = s"avail = ${if (isAvailable(let)) "T" else "f"}"
      val flags = s"$sentS, $closedS, $availS"
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

    def onPush(): Unit =
      if (remain == 0) {
        read()
        process()
      }

    def tryFree(): Unit =
      if (buf != null) {
        buf.release()
        buf = null
      }

    override def onUpstreamFinish(): Unit = process()
  }

  override def preStart(): Unit =
    shape.inlets.foreach(pull(_))

  override def postStop(): Unit = {
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

  private def process(): Unit = {
    // becomes `true` if state changes,
    // in that case we run this method again.
    var stateChange = false

    // println("process()")
    // println(s"  inputs: ${inputs.mkString("\n          ")}")

    if (sizeRemain == 0 && isAvailable(shape.size)) readSize()

    if (shouldNext) {
      inIndex += 1
      if (inIndex == numInputs) inIndex = 0
      // println(s"shouldNext($inIndex)")
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
      // println(s"inWinRem($inWinRem)")
      if (outSent) {
        bufOut        = allocOutBuf()
        outRemain     = bufOut.size
        outOff        = 0
        outSent       = false
        stateChange   = true
      }

      val chunk0  = math.min(inWinRem, outRemain)
      val chunk   = if (sizeRemain == 0 && isClosed(shape.size)) chunk0 else math.min(chunk0, sizeRemain)
      if (chunk > 0) {
        // println(s"chunk($chunk) << ${in.off}, ${in.remain}, $outOff, $outRemain, $winRemain")
        Util.copy(in.buf.buf, in.off, bufOut.buf, outOff, chunk)
        in.off     += chunk
        in.remain  -= chunk
        outOff     += chunk
        outRemain  -= chunk
        winRemain  -= chunk
        // println(s"chunk($chunk) >> ${in.off}, ${in.remain}, $outOff, $outRemain, $winRemain")
        if (sizeRemain > 0) {
          sizeOff    += chunk
          sizeRemain -= chunk
        }
        if (winRemain == 0) {
          // println("isNextWindow = true")
          isNextWindow = true
        }
        stateChange = true
      }
    }

    val flush = in.remain == 0 && isClosed(in.let)
    if (!outSent && (outRemain == 0 || flush) && isAvailable(shape.out)) {
      // println(s"push($outOff)")
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

    if      (flush && outSent) completeStage()
    else if (stateChange)      process()
  }

  inputs.foreach(in => setHandler(in.let, in))

  setHandler(shape.size, new InHandler {
    def onPush(): Unit = updateSize()

    override def onUpstreamFinish(): Unit = ()  // keep running
  })

  setHandler(shape.out, new OutHandler {
    def onPull(): Unit = process()
  })
}