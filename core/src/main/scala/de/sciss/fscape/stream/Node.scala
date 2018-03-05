/*
 *  Node.scala
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

import akka.stream.stage.GraphStageLogic

trait Node {
  _: GraphStageLogic =>

  // ---- abstract ----

  implicit protected def control: Control

  def failAsync(ex: Exception): Unit

  // ---- impl ----

  control.addNode(this)

  /** Calls `stopped` and then removes the node from the control. */
  override final def postStop(): Unit = {
    stopped()
    control.removeNode(this)
  }

  /** Subclasses can override this */
  protected def stopped(): Unit = ()
}