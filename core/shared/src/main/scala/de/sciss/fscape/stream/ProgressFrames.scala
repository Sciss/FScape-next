/*
 *  ProgressFrames.scala
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

package de.sciss.fscape.stream

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.fscape.stream.impl.shapes.SinkShape2
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object ProgressFrames {
  def apply(in: OutA, numFrames: OutL, label: String)(implicit b: Builder): Unit = {
    val stage0  = new Stage(layer = b.layer, label = label)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(numFrames , stage.in1)
  }

  private final val name = "ProgressFrames"

  private type Shp = SinkShape2[BufLike, BufL]

  private final class Stage(layer: Layer, label: String)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = SinkShape2(
      in0 = InA(s"$name.in"),
      in1 = InL(s"$name.numFrames")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(layer = layer, shape = shape, label = label)
  }

  private final class Logic(shape: Shp, layer: Layer, label: String)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler { node =>

    private[this] val key       = ctrl.mkProgress(label)
    private[this] var hasFrames = false

    private[this] var numFrames: Long = _

    private[this] var read = 0L

    private[this] var lastTime  = 0L
    private[this] var lastP500  = -1

    override def toString = s"$name-L($label)"

    private object FramesHandler extends InHandler {
      def onPush(): Unit = {
        val b = grab(shape.in1)
        if (!hasFrames && b.size > 0) {
          numFrames = math.max(1L, b.buf(0))
//          println(s"HAS_FRAMES $numFrames")
          hasFrames = true
          updateFraction()
        }
        b.release()
        tryPull(shape.in1)
      }

      override def onUpstreamFinish(): Unit =
//        if (isAvailable(shape.in1)) onPush()
//        else
        if (!hasFrames) {
          super.onUpstreamFinish()
        }
    }

    private def updateFraction(): Unit = {
      val p500_0  = (read * 500 / numFrames).toInt
      val p500    = if (p500_0 <= 500) p500_0 else 500
//      println(s"updateFraction() $p500")
      if (p500 > lastP500) {
        val time = System.currentTimeMillis()
        if (p500 == 500 || time - lastTime > 100) { // always report the 100% mark
          lastP500  = p500
          lastTime  = time
          val f     = p500 * 0.002
          ctrl.setProgress(key, f)
        }
      }
    }

    def onPush(): Unit = {
      val b = grab(shape.in0)
      read += b.size
      b.release()
      if (hasFrames) updateFraction()
      tryPull(shape.in0)
    }

//    override def onUpstreamFinish(): Unit = {
//      if (isAvailable(shape.in0)) onPush()
//      super.onUpstreamFinish()
//    }

    setHandler(shape.in0, this)
    setHandler(shape.in1, FramesHandler)
  }
}