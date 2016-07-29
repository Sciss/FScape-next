/*
 *  InOutImpl.scala
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

import akka.stream.{Inlet, Outlet, Shape}
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import de.sciss.fscape.stream.Control

trait InOutImpl[S <: Shape] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def shape: S

  implicit protected def ctrl: Control

  def process(): Unit

  /** Whether all inputs are available or have been closed and buffered. */
  def canRead: Boolean

  /** Whether all outputs are available for pushing. */
  def canWrite: Boolean

  /** Whether all input buffers are valid. */
  def inValid: Boolean

  /** Requests the update of the `canRead` status. */
  def updateCanRead(): Unit

  /** Requests the update of the `canWrite` status. */
  def updateCanWrite(): Unit

  protected def readIns(): Int

  protected def writeOuts(outOff: Int): Unit

    /** Exposed from `GraphStageLogic` API. */
  def completeStage(): Unit

  protected def freeInputBuffers (): Unit
  protected def freeOutputBuffers(): Unit

  // ---- impl ----

  /** Exposed from protected `GraphStageLogic` API. */
  final def isInAvailable[A](in: Inlet[A]): Boolean = isAvailable(in)

  /** Exposed from protected `GraphStageLogic` API. */
  final def isOutAvailable[A](out: Outlet[A]): Boolean = isAvailable(out)

  /** Exposed from protected `GraphStageLogic` API. */
  final def setInHandler[A](in: Inlet[A], h: InHandler): Unit = setHandler(in, h)

  /** Exposed from protected `GraphStageLogic` API. */
  final def setOutHandler[A](out: Outlet[A], h: OutHandler): Unit = setHandler(out, h)
}