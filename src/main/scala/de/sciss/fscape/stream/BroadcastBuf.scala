/*
 *  BroadcastBuf.scala
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

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import de.sciss.fscape.stream.impl.{StageImpl, StageLogicImpl}

import scala.collection.immutable.{IndexedSeq => Vec}

object BroadcastBuf {
  def apply[B <: BufLike](in: Outlet[B], numOutputs: Int)(implicit b: Builder): Vec[Outlet[B]] = {
    // println(s"BroadcastBuf($in, $numOutputs)")
    val stage0 = new Stage[B](numOutputs = numOutputs, eagerCancel = true)
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

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new Logic(shape = shape, eagerCancel = eagerCancel)
  }

  private final class Logic[B <: BufLike](shape: Shape[B], eagerCancel: Boolean)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape) with InHandler {

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
              logStream(s"completeStage() $this")
              completeStage()
            }
            else {
              sinksRunning -= 1
              if (sinksRunning == 0) {
                logStream(s"completeStage() $this")
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