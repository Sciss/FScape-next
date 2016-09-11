/*
 *  Elastic.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterIn2DImpl, StageImpl, NodeImpl}

import scala.annotation.tailrec

object Elastic {
  def apply(in: OutD, num: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in , stage.in0)
    b.connect(num, stage.in1)
    stage.out
  }

  private final val name = "Elastic"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in" ),
      in1 = InI (s"$name.num"),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with FilterIn2DImpl[BufD, BufI] {

    private[this] var init  = true

    private[this] var num     : Int         = _
    private[this] var buffers : Array[BufD] = _

    private[this] var bufRead     = 0
    private[this] var bufWritten  = 0

    @inline
    private def canPopBuf  = bufRead < bufWritten

    @inline
    private def canPushBuf = bufWritten - bufRead < num

    @inline
    private def shouldRead  = canRead  && bufIn0 == null

    @inline
    private def shouldWrite = canWrite && (canPopBuf  || bufIn0 != null)

    override protected def stopped(): Unit = {
      super.stopped()
      while (bufRead < bufWritten) {
        val idx = bufRead % num
        buffers(bufRead % num).release()
        buffers(idx) = null
        bufRead += 1
      }
    }

    private def pushBuffer(): Unit = {
      val idx = bufWritten % num
      buffers(idx) = bufIn0
      bufIn0 = null
      bufWritten += 1
    }

    @inline
    private def popBuffer(): BufD = {
      val idx = bufRead % num
      val res = buffers(idx)
      buffers(idx) = null
      bufRead += 1
      if (bufIn0 != null) pushBuffer()
      res
    }

    @tailrec
    def process(): Unit = {
      logStream(s"process() $this")
      // println(s"-- canRead? $canRead; canWrite? $canWrite; bufIn0 ${bufIn0 != null}; read $bufRead; written $bufWritten")
      var stateChange = false

      if (shouldRead) {
        assert(bufIn0 == null)
        readIns()
        assert(bufIn0 != null)
        // println(s"elastic: $bufIn0 | ${bufIn0.allocCount()}")
        // println(s"ela in : ${bufIn0.hashCode.toHexString} - ${bufIn0.buf.toVector.hashCode.toHexString}")

        if (init) {
          num     = math.max(0, bufIn1.buf(0))
          buffers = new Array[BufD](num)
          if (num > 0) pushBuffer()
          init    = false
        } else if (canPushBuf) {
          pushBuffer()
        }
        stateChange = true
      }

      if (shouldWrite) {
        val buf = if (canPopBuf) {
          popBuffer()
        } else {
          val res = bufIn0
          bufIn0 = null
          res
        }
        assert(buf != null)
        // println(s"ela out: ${buf.hashCode.toHexString} - ${buf.buf.toVector.hashCode.toHexString}")
        push(out0, buf)
        updateCanWrite()
        stateChange = true
      }

      if (isClosed(in0) && !canPopBuf && bufIn0 == null) {
        logStream(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }
  }
}