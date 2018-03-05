/*
 *  BroadcastBuf.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import de.sciss.fscape.stream.impl.{StageImpl, NodeImpl}

import scala.collection.immutable.{IndexedSeq => Vec}

object BroadcastBuf {
  def apply[B <: BufLike](in: Outlet[B], numOutputs: Int)(implicit b: Builder): Vec[Outlet[B]] = {
    // N.B. `eagerCancel` means that if we have out-degree two and
    // any of the two sinks closes, the entire `BroadcastBuf` closes.
    // This is usually _not_ what we want. We want to be able to
    // keep feeding the remaining sink.
    val stage0 = new Stage[B](numOutputs = numOutputs, eagerCancel = false)
    val stage  = b.add(stage0)
    b.connect(in, stage.in)
    stage.outArray.toIndexedSeq
  }

  private final val name = "BroadcastBuf"

  private type Shape[B <: BufLike] = UniformFanOutShape[B, B]

  /** Variant of Akka's built-in `Broadcast` that properly allocates buffers. */
  private final class Stage[B <: BufLike](numOutputs: Int, eagerCancel: Boolean)(implicit ctrl: Control)
    extends StageImpl[Shape[B]](name) {

    val shape: Shape = UniformFanOutShape(
      Inlet [B](s"$name.in"),
      Vector.tabulate(numOutputs)(i => Outlet[B](s"$name.out$i")): _*
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape = shape, eagerCancel = eagerCancel)
  }

  private final class Logic[B <: BufLike](shape: Shape[B], eagerCancel: Boolean)(implicit ctrl: Control)
    extends NodeImpl(name, shape) with InHandler { self =>

    private[this] val numOutputs    = shape.outArray.length
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

    def onPush(): Unit = {
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
    }

    private def tryPull(): Unit =
      if (pendingCount == 0 && !hasBeenPulled(shape.in)) pull(shape.in)

    {
      var idx = 0
      while (idx < numOutputs) {
        val out = shape.out(idx)
        val idx0 = idx // fix for OutHandler
        setHandler(out, new OutHandler {
          def onPull(): Unit = {
            pending(idx0) = false
            pendingCount -= 1
            tryPull()
          }

          override def onDownstreamFinish(): Unit = {
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
                tryPull()
              }
            }
          }
        })
        idx += 1
      }
    }
  }
}