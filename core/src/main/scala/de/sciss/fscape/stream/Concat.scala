/*
 *  Concat.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{FullInOutImpl, NodeImpl, Out1LogicImpl, ProcessOutHandlerImpl, SameChunkImpl, StageImpl}

object Concat {
  def apply[A, E >: Null <: BufElem[A]](a: Outlet[E], b: Outlet[E])
                                       (implicit builder: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](builder.layer)
    val stage   = builder.add(stage0)
    builder.connect(a, stage.in0)
    builder.connect(b, stage.in1)
    stage.out
  }

  private final val name = "Concat"

  private type Shape[E] = FanInShape2[E, E, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shape[E]](name) {

    val shape = new FanInShape2(
      in0 = Inlet [E](s"$name.a"  ),
      in1 = Inlet [E](s"$name.b"  ),
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer)
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with FullInOutImpl[Shape[E]]
      with SameChunkImpl[Shape[E]]
      with Out1LogicImpl[E, Shape[E]] {

    // ---- impl ----

    protected var bufIn0 : E = _
    protected var bufOut0: E = _

    protected val in0 : Inlet [E] = shape.in0
    protected val in1 : Inlet [E] = shape.in1
    protected val out0: Outlet[E] = shape.out

    private[this] var _canRead = false

    def canRead: Boolean = _canRead
    def inValid: Boolean = true

    protected def allocOutBuf0(): E = tpe.allocBuf()

    override protected def stopped(): Unit = {
      super.stopped()
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

    protected def shouldComplete(): Boolean =
      inRemain == 0 && isClosed(in0) && !isAvailable(in0) && isClosed(in1) && !isAvailable(in1)

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit =
      System.arraycopy(bufIn0.buf, inOff, bufOut0.buf, outOff, chunk)

    private object _InHandlerImpl extends InHandler {
      def onPush(): Unit = {
        updateCanRead()
        if (canRead) process()
      }

      override def onUpstreamFinish(): Unit = {
        updateCanRead()
        process()
      }
    }

    setHandler(in0, _InHandlerImpl)
    setHandler(in1, _InHandlerImpl)
    new ProcessOutHandlerImpl(out0, this)
  }
}