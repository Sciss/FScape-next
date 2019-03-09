/*
 *  OnComplete.scala
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
package lucre.stream

import akka.stream.{Attributes, ClosedShape}
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{Builder, Control}

object OnComplete {
  def apply(ref: Input.Action.Value)(implicit b: Builder): Unit = {
    val stage0 = new Stage(ref)
    b.add(stage0)
  }

  private final val name = "OnComplete"

  private type Shape = ClosedShape

  private final class Stage(ref: Input.Action.Value)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape: Shape = ClosedShape

    def createLogic(attr: Attributes) = new Logic(shape, ref)
  }

  private final class Logic(shape: Shape, ref: Input.Action.Value)(implicit ctrl: Control)
    extends NodeImpl(name, shape) with NodeHasInitImpl {

    override protected def init(): Unit = {
      super.init()
      import ctrl.config.executionContext
      ctrl.status.onComplete(ref.execute(_))
    }
  }
}