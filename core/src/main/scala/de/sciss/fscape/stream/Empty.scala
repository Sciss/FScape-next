/*
 *  Empty.scala
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

import akka.stream.stage.OutHandler
import akka.stream.{Attributes, SourceShape}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object Empty {
  def apply()(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "Empty"

  private type Shape = SourceShape[BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new SourceShape(OutD(s"$name.out"))

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with OutHandler {

    override protected def launch(): Unit = completeStage()

    def onPull(): Unit = ()

    setHandler(shape.out, this)
  }
}