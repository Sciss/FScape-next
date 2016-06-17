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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterIn2DImpl, StageImpl, StageLogicImpl}

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

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with FilterIn2DImpl[BufD, BufI] {

    private[this] var init  = true

    private[this] var num     : Int         = _
    private[this] var buffers : Array[BufD] = _

    private[this] var bufPopIdx   = 0   // circular
    private[this] var bufPushIdx  = 0   // circular

    @inline
    private def canUseBuf  = bufPopIdx != bufPushIdx

    @inline
    private def shouldRead  = canRead  && (canUseBuf  || bufIn0 == null)

    @inline
    private def shouldWrite = canWrite && (canUseBuf  || bufIn0 != null)

    override def postStop(): Unit = {
      super.postStop()
      println("POST STOP")
      while (bufPopIdx < bufPushIdx) {
        buffers(bufPopIdx).release()
        buffers(bufPopIdx) = null
        bufPopIdx = (bufPopIdx + 1) % num
      }
    }

    def logStreamX(what: => String): Unit = {
      println(what)
    }

    private def pushBuffer(): Unit = {
      println(s"push buffers($bufPushIdx) = $bufIn0")
      assert(bufIn0 != null)
      buffers(bufPushIdx) = bufIn0
      bufIn0 = null
      bufPushIdx = (bufPushIdx + 1) % num
      println(s"bufPopIdx = $bufPopIdx; bufPushIdx = $bufPushIdx")
    }

    @tailrec
    def process(): Unit = {
      logStreamX(s"process() $this")
      logStreamX(s"-- canRead? $canRead; canWrite? $canWrite; bufIn0 ${bufIn0 != null}; readIdx $bufPopIdx; writeIdx $bufPushIdx")
      var stateChange = false

      if (shouldRead) {
        readIns()
        assert(bufIn0 != null)

        if (init) {
          num     = math.max(0, bufIn1.buf(0))
          buffers = new Array[BufD](num)
          if (num > 0) pushBuffer()
          init    = false
        } else if (canUseBuf) {
          pushBuffer()
        }
        stateChange = true
      }

      if (shouldWrite) {
        val buf = if (canUseBuf) {
          val res = buffers(bufPopIdx)
          println(s"read buffers($bufPopIdx) = $res; -> set null")
          buffers(bufPopIdx) = null
          bufPopIdx = (bufPopIdx + 1) % num
          println(s"bufPopIdx = $bufPopIdx; bufPushIdx = $bufPushIdx")
          res
        } else {
          val res = bufIn0
          bufIn0 = null
          res
        }
        assert(buf != null)
        push(out0, buf)
        updateCanWrite()
        stateChange = true
      }

      if (isClosed(in0) && !canUseBuf && bufIn0 == null) {
        logStreamX(s"completeStage() $this")
        completeStage()
      }
      else if (stateChange) process()
    }
  }
}