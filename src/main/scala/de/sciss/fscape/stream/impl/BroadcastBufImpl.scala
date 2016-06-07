/*
 *  BroadcastBufImpl.scala
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
package impl

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}

/** Variant of Akka's built-in `Broadcast` that properly allocates buffers. */
final class BroadcastBufStageImpl[B <: BufLike](numOutputs: Int, eagerCancel: Boolean)(implicit ctrl: Control)
  extends GraphStage[UniformFanOutShape[B, B]] {
  
  override def initialAttributes = Attributes.name(toString)

  val shape: UniformFanOutShape[B, B] = UniformFanOutShape(
    Inlet [B]("BroadcastBuf.in"),
    Vector.tabulate(numOutputs)(i => Outlet[B](s"BroadcastBuf.out$i")): _*
  )

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new BroadcastBufLogicImpl(shape = shape, eagerCancel = eagerCancel)

  override def toString = "BroadcastBuf"
}

final class BroadcastBufLogicImpl[B <: BufLike](shape: UniformFanOutShape[B, B], eagerCancel: Boolean)
                                               (implicit ctrl: Control)
  extends GraphStageLogic(shape) with InHandler {

  private[this] val numOutputs    = shape.outArray.length
  private[this] var pendingCount  = numOutputs
  private[this] val pending       = Array.fill[Boolean](numOutputs)(true)
  private[this] var sinksRunning  = numOutputs

  setHandler(shape.in, this)

  def onPush(): Unit = {
    pendingCount  = sinksRunning
    val buf       = grab(shape.in)

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
            logStream(s"$this.completeStage()")
            completeStage()
          }
          else {
            sinksRunning -= 1
            if (sinksRunning == 0) {
              logStream(s"$this.completeStage()")
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
