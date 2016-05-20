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

package de.sciss.fscape.stream.impl

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import de.sciss.fscape.stream.{BufLike, Control}

/** Variant of Akka's built-in `Broadcast` that properly allocates buffers. */
final class BroadcastBufStageImpl[B <: BufLike](numOutputs: Int, eagerCancel: Boolean, ctrl: Control)
  extends GraphStage[UniformFanOutShape[B, B]] {
  
  override def initialAttributes = Attributes.name(toString)

  val shape: UniformFanOutShape[B, B] = UniformFanOutShape(
    Inlet [B]("BroadcastBuf.in"),
    Vector.tabulate(numOutputs)(i => Outlet[B](s"BroadcastBuf.out$i")): _*
  )

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new BroadcastBufLogicImpl(shape = shape, eagerCancel = eagerCancel, ctrl = ctrl)

  override def toString = "BroadcastBuf"
}

final class BroadcastBufLogicImpl[B <: BufLike](shape: UniformFanOutShape[B, B], eagerCancel: Boolean, ctrl: Control)
  extends GraphStageLogic(shape) with InHandler {

  private[this] val numOutputs    = shape.outArray.length
  private[this] var pendingCount  = numOutputs
  private[this] val pending       = Array.fill[Boolean](numOutputs)(true)
  private[this] var sinksRunning  = numOutputs

  setHandler(shape.in, this)

  def onPush(): Unit = {
    pendingCount  = sinksRunning
    val elem      = grab(shape.in)

    var idx = 0
    while (idx < numOutputs) {
      val out = shape.out(idx)
      if (!isClosed(out)) {
        push(out, elem)
        pending(idx) = true
      }
      idx += 1
    }
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
          if (eagerCancel) completeStage()
          else {
            sinksRunning -= 1
            if (sinksRunning == 0) completeStage()
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
