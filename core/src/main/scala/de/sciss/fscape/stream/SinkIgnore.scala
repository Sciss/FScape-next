package de.sciss.fscape.stream

import akka.stream.stage.InHandler
import akka.stream.{Attributes, Inlet, Outlet, SinkShape}
import de.sciss.fscape.{logStream, stream}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object SinkIgnore {
  def apply[E <: BufLike](in: Outlet[E])(implicit b: stream.Builder): Unit = {
    val stage0  = new Stage[E](layer = b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
  }

  private final val name = "SinkIgnore"

  private type Shape[E <: BufLike] = SinkShape[E]

  private final class Stage[E <: BufLike](layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape[E]](name) {
    val shape: Shape = SinkShape[E](
      in = Inlet[E](s"$name.in")
    )

    def createLogic(attr: Attributes) = new Logic(shape = shape, layer = layer)
  }

  private final class Logic[E <: BufLike](shape: Shape[E], layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with InHandler {

    setHandler(shape.in, this)

    override protected def launch(): Unit = {
      logStream(s"$this - launch")
      completeStage()
      // super.launch()
    }

    // should never get here
    def onPush(): Unit = {
      val buf = grab(shape.in)
      buf.release()
      tryPull(shape.in)
    }
  }
}
