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