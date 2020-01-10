/*
 *  BinaryOp.scala
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

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.math.min

object BinaryOp {
  import graph.BinaryOp.Op

  def apply(op: Op, in1: OutD, in2: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(layer = b.layer, op = op)
    val stage   = b.add(stage0)
    b.connect(in1, stage.in0)
    b.connect(in2, stage.in1)
    stage.out
  }

  private final val name = "BinaryOp"

  private type Shape = FanInShape2[BufD, BufD, BufD]

  private final class Stage(layer: Layer, op: Op)(implicit ctrl: Control) extends StageImpl[Shape](s"$name(${op.name})") {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in1"),
      in1 = InD (s"$name.in2"),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer = layer, op = op)
  }

  private final class Logic(shape: Shape, layer: Layer, op: Op)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${op.name})", layer, shape) with OutHandler { logic =>

    private[this] val hA = new _InHandlerImpl(shape.in0)
    private[this] val hB = new _InHandlerImpl(shape.in1)

    private[this] var inDataRem = 2

    private[this] var bufOut: BufD = _
    private[this] var outOff = 0

    private[this] var aVal: Double = _
    private[this] var bVal: Double = _

    setHandler(shape.out, this)

    override protected def stopped(): Unit = {
      super.stopped()
      hA.freeBuffer()
      hB.freeBuffer()
      freeOutBuffer()
    }

    private final class _InHandlerImpl(in: InD)
      extends InHandler {

      private[this] var hasValue      = false
      private[this] var everHadValue  = false

      private[this] var _buf    : BufD  = _
      private[this] var _offset : Int   = 0

      def buf: BufD = _buf

      def offset: Int = _offset

      def bufRemain: Int = if (_buf == null) 0 else _buf.size - _offset

      override def toString: String = s"$logic .${in.s}"

      def updateOffset(n: Int): Unit =
        if (_buf != null) {
          _offset = n
          assert (_offset <= _buf.size)
          if (bufRemain == 0) freeBuffer()
        }

      def hasNext: Boolean =
        (_buf != null) || !isClosed(in) || isAvailable(in)

      def freeBuffer(): Unit =
        if (_buf != null) {
          _buf.release()
          _buf = null
          _offset = 0
        }

      def next(): Unit = {
        hasValue = false
        val r = bufRemain > 0
        logStream(s"next() $this - bufRemain > 0 ? $r")
        if (r) {
          ackValue()
        } else {
          freeBuffer()
          if (isAvailable(in)) onPush()
        }
      }

      def onPush(): Unit = {
        logStream(s"onPush() $this - hasValue ? $hasValue")
        if (!hasValue) {
          assert(_buf == null)
          _buf = grab(in)
          assert(_buf.size > 0)
          ackValue()
          tryPull(in)
        }
      }

      private def ackValue(): Unit = {
        hasValue      = true
        everHadValue  = true
        inDataRem -= 1
        logStream(s"ackValue() $this - inDataRem = $inDataRem")
        if (inDataRem == 0) {
          notifyInDataReady()
        }
      }

      override def onUpstreamFinish(): Unit = {
        val hasMore = isAvailable(in)
        logStream(s"onUpstreamFinish() $this - hasMore = $hasMore, hasValue = $hasValue, everHadValue = $everHadValue")
        if (!hasMore) {
          if (everHadValue) {
            if (!hasValue) ackValue()
          } else {
            super.onUpstreamFinish()
          }
        }
      }

      setHandler(in, this)
    }

    private def writeOut(): Unit = {
      logStream(s"writeOut() $this")
      if (outOff > 0) {
        bufOut.size = outOff
        push(shape.out, bufOut)
        outOff = 0
        bufOut = null
      } else {
        freeOutBuffer()
      }
    }

    private def freeOutBuffer(): Unit =
      if (bufOut != null) {
        bufOut.release()
        bufOut = null
      }

    private def tryWriteFull(): Boolean =
      (bufOut != null && outOff == bufOut.size && isAvailable(shape.out)) && {
        writeOut()
        true
      }

    private[this] var inDataReady = false

    private def notifyInDataReady(): Unit = {
      logStream(s"notifyInDataReady() $this")
      assert (!inDataReady)
      inDataReady = true
      processInData()
    }

    private def processInData(): Unit = {
      logStream(s"processInData() $this")
      assert (inDataReady)

      if (bufOut == null) bufOut = ctrl.borrowBufD()

      var chunk = bufOut.size - outOff
      if (chunk == 0) return

      var outOffI = outOff
      val aBuf    = hA.buf
      val bBuf    = hB.buf
      if (aBuf == null && bBuf == null) {
        if (isAvailable(shape.out)) {
          writeOut()
          completeStage()
        }
        return
      }

      var aOff    = hA.offset
      var bOff    = hB.offset
      val aStop   = if (aBuf == null) 0 else {
        val sz    = aBuf.size
        val len   = sz - aOff
        chunk     = min(chunk, len)
        sz
      }
      val bStop   = if (bBuf == null) 0 else {
        val sz    = bBuf.size
        val len   = sz - bOff
        chunk     = min(chunk, len)
        sz
      }
      val out     = bufOut.buf
      var av      = aVal
      var bv      = bVal
      val aArr    = if (aBuf == null) null else aBuf.buf
      val bArr    = if (bBuf == null) null else bBuf.buf
      val outStop = outOffI + chunk
      while (outOffI < outStop) {
        if (aOff < aStop) {
          av = aArr(aOff)
          aOff += 1
        }
        if (bOff < bStop) {
          bv = bArr(bOff)
          bOff += 1
        }
        out(outOffI) = op(av, bv)
        outOffI += 1
      }
      aVal    = av
      bVal    = bv
      outOff  = outStop

      hA.updateOffset(aOff)
      hB.updateOffset(bOff)

      tryWriteFull()
      requestNextInData()
    }

    private def requestNextInData(): Unit = {
      inDataReady = false
      val aHas  = hA.hasNext
      val bHas  = hB.hasNext
      inDataRem = 0
      if (aHas) inDataRem += 1
      if (bHas) inDataRem += 1
      logStream(s"requestNextInData() $this aHas = $aHas, bHas = $bHas")

      if (inDataRem == 0) {
        if (outOff == 0) {
          completeStage()
        } else if (isAvailable(shape.out)) {
          writeOut()
          completeStage()
        } else {
          inDataReady = true
        }
      } else {
        if (aHas) hA.next()
        if (bHas) hB.next()
      }
    }

    def onPull(): Unit = {
      tryWriteFull()
      if (inDataReady) processInData()
    }
  }
}