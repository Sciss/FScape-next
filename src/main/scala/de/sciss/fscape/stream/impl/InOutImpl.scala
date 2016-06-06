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

  /** Whether all input buffers are valid. */
  def inValid: Boolean

  def updateCanRead(): Unit

  protected def readIns(): Int

  /** Exposed from `GraphStageLogic` API. */
  def completeStage(): Unit

  /** Exposed from protected `GraphStageLogic` API. */
  final def isInAvailable[A](in: Inlet[A]): Boolean = isAvailable(in)

  /** Exposed from protected `GraphStageLogic` API. */
  final def setInHandler[A](in: Inlet[A], h: InHandler): Unit = setHandler(in, h)

  /** Exposed from protected `GraphStageLogic` API. */
  final def setOutHandler[A](out: Outlet[A], h: OutHandler): Unit = setHandler(out, h)

  protected def freeInputBuffers (): Unit
  protected def freeOutputBuffers(): Unit
}
