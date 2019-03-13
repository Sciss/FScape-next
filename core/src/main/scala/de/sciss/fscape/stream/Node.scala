/*
 *  Node.scala
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

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

import scala.concurrent.Future

trait Node {
  _: GraphStageLogic =>

  // ---- abstract ----

  implicit protected def control: Control

  def layer: Layer

  def launchAsync(): Future[Unit]

  def completeAsync(): Future[Unit]

  def failAsync(ex: Exception): Unit

  def shape: Shape

  // ---- impl ----

  control.addNode(this)

  /** Final so we don't accidentally place code here.
    * In order to initialize state, use `NodeHasInitImpl`
    * and implement `init`.
    */
  override final def preStart(): Unit = ()

  /** Calls `stopped` and then removes the node from the control. */
  override final def postStop(): Unit = {
    stopped()
    control.removeNode(this)
  }

  /** Subclasses can override this */
  protected def stopped(): Unit = ()
}

trait NodeHasInit extends Node {
  _: GraphStageLogic =>

  def initAsync(): Future[Unit]
}