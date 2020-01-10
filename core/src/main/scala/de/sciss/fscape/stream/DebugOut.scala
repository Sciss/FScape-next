/*
 *  DebugOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.InHandler
import akka.stream.{Attributes, SinkShape}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object DebugOut {
  def apply(in: OutD)(implicit b: Builder): Unit = {
    val sink = new Stage(b.layer)
    val stage = b.add(sink)
    b.connect(in, stage.in)
  }

  private final val name = "DebugOut"

    private type Shape = SinkShape[BufD]

  private final class Stage(layer: Layer)(implicit protected val ctrl: Control)
    extends StageImpl[Shape](s"$name") {

    val shape: Shape = SinkShape[BufD](InD(s"$name.in"))

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(s"$name", layer, shape) with InHandler { logic =>

    setHandler(shape.in, this)

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish($this)")
      super.onUpstreamFinish()
    }

    override protected def stopped(): Unit = {
      logStream(s"$this - postStop()")
    }

    def onPush(): Unit = process()

    private def process(): Unit = {
      logStream(s"process() $this")
      val bufIn = grab(shape.in)
      bufIn.release()
      pull(shape.in)
    }
  }
}