/*
 *  Done.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

object Done {
  def apply(in: OutA)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Done"

  private type Shp = FlowShape[BufLike, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet[BufLike](s"$name.in" ),
      out = OutI          (s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler with OutHandler {

    setHandler(shape.in , this)
    setHandler(shape.out, this)

    override def onUpstreamFinish(): Unit = {
      if (/* !isAvailable(shape.in) && */ isAvailable(shape.out)) flush()
    }

    def onPull(): Unit = {
      if (isClosed(shape.in) /* && !isAvailable(shape.in) */) flush()
    }

//    private var NUM = 0L

    def onPush(): Unit = {
      val buf = grab(shape.in)
//      NUM += buf.size
      buf.release()
      pull(shape.in)
    }

    private def flush(): Unit = {
      val buf    = ctrl.borrowBufI()
      buf.buf(0) = 1
      buf.size   = 1
      push(shape.out, buf)
//      println(s"NUM = $NUM")
      logStream.info(s"completeStage() $this")
      completeStage()
    }
  }
}