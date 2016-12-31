/*
 *  Node.scala
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

import akka.stream.stage.GraphStageLogic

trait Node {
  _: GraphStageLogic =>

  // ---- abstract ----

  implicit protected def control: Control

  def failAsync(ex: Exception): Unit

  // ---- impl ----

  control.addNode(this)

  override final def postStop(): Unit = {
    control.removeNode(this)
    stopped()
  }

  /** Subclasses can override this */
  protected def stopped(): Unit = ()
}