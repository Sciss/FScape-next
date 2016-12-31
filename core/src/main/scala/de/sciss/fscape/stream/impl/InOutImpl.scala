/*
 *  InOutImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Inlet, Outlet, Shape}

trait InOutImpl[S <: Shape] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def shape: S

  implicit protected def control: Control

  def process(): Unit

  /** Whether all outputs are available for pushing. */
  def canWrite: Boolean

  /** Requests the update of the `canWrite` status. */
  def updateCanWrite(): Unit

  protected def writeOuts(outOff: Int): Unit

    /** Exposed from `GraphStageLogic` API. */
  def completeStage(): Unit

  protected def freeOutputBuffers(): Unit

  /** Whether all input buffers are valid. */
  def inValid: Boolean

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

trait FullInOutImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected def readIns(): Int

  /** Whether all inputs are available or have been closed and buffered. */
  def canRead: Boolean

  /** Requests the update of the `canRead` status. */
  def updateCanRead(): Unit

  protected def freeInputBuffers (): Unit
}

trait DemandInOutImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  protected def readAuxIns (): Int
  protected def readMainIns(): Int

  def auxInValid : Boolean
  def mainInValid: Boolean

  def auxCanRead : Boolean
  def mainCanRead: Boolean

  def updateAuxCanRead (): Unit
  def updateMainCanRead(): Unit
}
