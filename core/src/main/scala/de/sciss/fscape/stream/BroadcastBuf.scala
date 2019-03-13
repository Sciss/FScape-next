/*
 *  BroadcastBuf.scala
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

object BroadcastBuf {
  def apply[B <: BufLike](in: Outlet[B], numOutputs: Int)(implicit b: Builder): Vec[Outlet[B]] = {
    // N.B. `eagerCancel` means that if we have out-degree two and
    // any of the two sinks closes, the entire `BroadcastBuf` closes.
    // This is usually _not_ what we want. We want to be able to
    // keep feeding the remaining sink.
    val stage0 = new Stage[B](layer = b.layer, numOutputs = numOutputs, eagerCancel = false)
    val stage  = b.add(stage0)
    b.connect(in, stage.in)
    stage.outlets.toIndexedSeq
  }

  private final val name = "BroadcastBuf"

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

  private final class Logic[B <: BufLike](shape: Shape[B], layer: Layer, eagerCancel: Boolean)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler { self =>

    private[this] val numOutputs    = shape.outlets.size
    private[this] var pendingCount  = numOutputs
    private[this] val pending       = Array.fill[Boolean](numOutputs)(true)
    private[this] var sinksRunning  = numOutputs

    setHandler(shape.in, this)

//    private var NUM = 0L
//
//    override def onUpstreamFinish(): Unit = {
//      println(s"BROADCAST $NUM")
//      super.onUpstreamFinish()
//    }

    // XXX TODO --- broad cast are synthetic elements, they
    // may connect two layers, so we should not include them in
    // explicit shut down (?)
    override def completeAsync(): Future[Unit] = Future.unit // super.completeAsync()

    def onPush(): Unit = {
      logStream(s"onPush() $this")
      if (pendingCount == 0) process()
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish() $this")
      checkProcess() // super.onUpstreamFinish()
    }

    private def process(): Unit = {
      logStream(s"process() $this")
      pendingCount  = sinksRunning
      val buf       = grab(shape.in)

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

      tryPull(shape.in)
    }

    private def checkProcess(): Unit =
      if (isAvailable(shape.in)) {
        if (pendingCount == 0) process()
      } else if (isClosed(shape.in)) {
        completeStage()
      }

    {
      var idx = 0
      while (idx < numOutputs) {
        val out = shape.out(idx)
        val idx0 = idx // fix for OutHandler
        setHandler(out, new OutHandler {
          def onPull(): Unit = {
            logStream(s"onPull() $self.${out.s}")
            pending(idx0) = false
            pendingCount -= 1
            checkProcess()
          }

          override def onDownstreamFinish(): Unit = {
            logStream(s"onDownstreamFinish() $self.${out.s}")
            if (eagerCancel) {
              logStream(s"completeStage() $self")
              completeStage()
            }
            else {
              sinksRunning -= 1
              if (sinksRunning == 0) {
                logStream(s"completeStage() $self")
                completeStage()
              }
              else if (pending(idx0)) {
                pending(idx0) = false
                pendingCount -= 1
                checkProcess()
              }
            }
          }
        })
        idx += 1
      }
    }
  }
}