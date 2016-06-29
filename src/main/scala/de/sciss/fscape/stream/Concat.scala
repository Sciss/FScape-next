/*
 *  Concat.scala
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

import akka.stream.stage.{GraphStageLogic, InHandler}
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{SameChunkImpl, InOutImpl, Out1DoubleImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl, StageLogicImpl}

/** Binary operator assuming stream is complex signal (real and imaginary interleaved).
  * Outputs another complex stream even if the operator yields a purely real-valued result.
  */
object Concat {
  def apply(a: OutD, b: OutD)(implicit builder: Builder): OutD = {
    val stage0  = new Stage
    val stage   = builder.add(stage0)
    builder.connect(a, stage.in0)
    builder.connect(b, stage.in1)
    stage.out
  }

  private final val name = "Concat"

  private type Shape = FanInShape2[BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.a" ),
      in1 = InD (s"$name.b" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO --- abstract across BufI / BufD?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with InOutImpl[Shape]
      with SameChunkImpl[Shape]
      with Out1LogicImpl[BufD, Shape]
      with Out1DoubleImpl[Shape] {


    // ---- impl ----

    protected var bufIn0 : BufD = _
    protected var bufOut0: BufD = _

    protected val in0  = shape.in0
    protected val in1  = shape.in1
    protected val out0 = shape.out

    private[this] var _canRead = false

    def canRead: Boolean = _canRead
    def inValid: Boolean = true

    override def preStart(): Unit = {
      pull(in0)
      pull(in1)
    }

    override def postStop(): Unit = {
      freeInputBuffers()
      freeOutputBuffers()
    }

    protected def readIns(): Int = {
      freeInputBuffers()

      if (isAvailable(in0)) {
        bufIn0 = grab(in0)
        bufIn0.assertAllocated()
        tryPull(in0)
      } else {
        assert(isClosed(in0) && isAvailable(in1))
        bufIn0 = grab(in1)
        tryPull(in1)
      }

      updateCanRead()
      bufIn0.size
    }

    protected def freeInputBuffers(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    def updateCanRead(): Unit =
      _canRead = isAvailable(in0) || (isClosed(in0) && isAvailable(in1))

    protected def shouldComplete(): Boolean = inRemain == 0 && isClosed(in0) && isClosed(in1)

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, bufOut0.buf, outOff, chunk)

    private object InHandlerImpl extends InHandler {
      def onPush(): Unit = {
        updateCanRead()
        if (canRead) process()
      }

      override def onUpstreamFinish(): Unit = {
        updateCanRead()
        process()
      }
    }

    setHandler(in0, InHandlerImpl)
    setHandler(in1, InHandlerImpl)
    new ProcessOutHandlerImpl(out0, this)
  }
}