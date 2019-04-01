/*
 *  Broadcast.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future

object Broadcast {
  def apply[B <: BufLike](in: Outlet[B], numOutputs: Int)(implicit b: Builder): Vec[Outlet[B]] = {
    // N.B. `eagerCancel` means that if we have out-degree two and
    // any of the two sinks closes, the entire `Broadcast` closes.
    // This is usually _not_ what we want. We want to be able to
    // keep feeding the remaining sink.
    val stage0 = new Stage[B](layer = b.layer, numOutputs = numOutputs, eagerCancel = false)
    val stage  = b.add(stage0)
    b.connect(in, stage.in)
    stage.outlets.toIndexedSeq
  }

  private final val name = "Broadcast"

  private type Shape[B <: BufLike] = UniformFanOutShape[B, B]

  /** Variant of Akka's built-in `Broadcast` that properly allocates buffers. */
  private final class Stage[B <: BufLike](layer: Layer, numOutputs: Int, eagerCancel: Boolean)(implicit ctrl: Control)
    extends StageImpl[Shape[B]](name) {

    val shape: Shape = UniformFanOutShape(
      Inlet [B](s"$name.in"),
      Vector.tabulate(numOutputs)(i => Outlet[B](s"$name.out$i")): _*
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape = shape, layer = layer, eagerCancel = eagerCancel)
  }

  // not available in Scala 2.11
  private val futureUnit: Future[Unit] = Future.successful(())

  private final class Logic[B <: BufLike](shape: Shape[B], layer: Layer, eagerCancel: Boolean)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler { self =>

    private[this] val numOutputs    = shape.outlets.size
    private[this] var pendingCount  = numOutputs
    private[this] val pending       = Array.fill[Boolean](numOutputs)(true)
    private[this] var sinksRunning  = numOutputs

    setHandler(shape.in, this)

    // XXX TODO --- broad cast are synthetic elements, they
    // may connect two layers, so we should not include them in
    // explicit shut down (?)
    override def completeAsync(): Future[Unit] = futureUnit // super.completeAsync()

    def onPush(): Unit = {
      logStream(s"onPush() $this")
      if (pendingCount == 0) process()
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish() $this")
      if (isAvailable(shape.in)) {
        if (pendingCount == 0) process()
      } else {
        completeStage()
      }
    }

    private def process(): Unit = {
      logStream(s"process() $this")
      pendingCount  = sinksRunning
      val in        = shape.in
      val buf       = grab(in)

//      NUM += buf.size

      // for N non-closed outputs,
      // we call `acquire` N times.
      // Initially the buffer will have
      // an allocation count of one.
      // Finally we call `release`,
      // so the final allocation count
      // will be N.
      var idx = 0
      while (idx < numOutputs) {
        val out = shape.out(idx)
        if (!isClosed(out)) {
          buf.acquire()
          push(out, buf)
          pending(idx) = true
        }
        idx += 1
      }
      buf.release()

      if (!isClosed(in)) {
        if (!hasBeenPulled(in)) { // WTF ???
          pull(in)
        }
      } else {
        completeStage()
      }
    }

    private final class OutHandlerImpl(out: Outlet[B], idx: Int) extends OutHandler {

      override def toString: String = s"$self.${out.s}"

      private def decPendingAndCheck(): Unit = {
        if (pending(idx)) {
          pending(idx) = false
          pendingCount -= 1
          if (pendingCount == 0) {
            // N.B.: Since we might not have proactively pulled the input
            // (which may happen if the node spans across layers),
            // we have to check that condition here and
            // issue the pull if necessary
            val in = shape.in
            if         (isAvailable(in)) process()
            else if (!hasBeenPulled(in)) tryPull(in)
          }
        }
      }

      def onPull(): Unit = {
        logStream(s"onPull() $self.${out.s}")
        decPendingAndCheck()
      }

      override def onDownstreamFinish(): Unit = {
        logStream(s"onDownstreamFinish() $self.${out.s}")
        if (eagerCancel) {
          logStream(s"completeStage() $self")
          super.onDownstreamFinish()
        }
        else {
          sinksRunning -= 1
          if (sinksRunning == 0) {
            logStream(s"completeStage() $self")
            super.onDownstreamFinish()
          } else {
            decPendingAndCheck()
          }
        }
      }

      setHandler(out, this)
    }

    {
      var idx = 0
      while (idx < numOutputs) {
        val out = shape.out(idx)
        new OutHandlerImpl(out, idx)
        idx += 1
      }
    }
  }
}