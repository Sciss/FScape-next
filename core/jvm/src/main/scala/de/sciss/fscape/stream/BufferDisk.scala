/*
 *  BufferDisk.scala
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

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl}
import de.sciss.fscape.Log.{stream => logStream}

// XXX TODO --- we could use a "quasi-circular"
// structure? this is, overwrite parts of the file
// that were already read
object BufferDisk {
  def apply[A, E <: BufElem[A]](in: Outlet[E])(implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "BufferDisk"

  private type Shp[E] = FlowShape[E, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends BlockingGraphStage[Shp[E]](name) {

    val shape: Shape = new FlowShape(
      in  = Inlet [E](s"$name.in" ),
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with InHandler with OutHandler {

    private[this] var af: FileBuffer[A]  = _
    private[this] val bufSize       = ctrl.blockSize

    private[this] var framesWritten = 0L
    private[this] var framesRead    = 0L

    setHandlers(shape.in, shape.out, this)

    override protected def init(): Unit = {
      super.init()
      af = FileBuffer()
    }

    override protected def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override protected def stopped(): Unit = {
      super.stopped()
      if (af != null) {
        af.dispose()
        af = null
      }
    }

    def onPush(): Unit = {
      val bufIn = grab(shape.in)
      tryPull(shape.in)
      val chunk = bufIn.size
      logStream.debug(s"onPush(${shape.in}) $chunk; read = $framesRead; written = $framesWritten")

      try {
        if (af.position != framesWritten) af.position = framesWritten
        af.write(bufIn.buf, 0, chunk)
        framesWritten += chunk
        // logStream(s"framesWritten = $framesWritten")
      } finally {
        bufIn.release()
      }

      onPull()
    }

    def onPull(): Unit = if (isInitialized && isAvailable(shape.out)) {
      val inputDone   = isClosed(shape.in) && !isAvailable(shape.in)
      val framesAvail = framesWritten - framesRead
      if (!inputDone && framesAvail < bufSize) return

      val chunk = math.min(bufSize, framesAvail).toInt
      logStream.debug(s"onPull(${shape.out}) $chunk; read = $framesRead; written = $framesWritten")
      if (chunk == 0) {
        if (inputDone) {
          logStream.info(s"onPull() -> completeStage $this")
          completeStage()
        }

      } else {
        if (af.position != framesRead) af.position = framesRead
        val bufOut = tpe.allocBuf() // ctrl.borrowBufD()
        af.read(bufOut.buf, 0, chunk)
        framesRead += chunk
        bufOut.size = chunk
        push(shape.out, bufOut)
      }
    }

    // in closed
    override def onUpstreamFinish(): Unit = {
      logStream.info(s"onUpstreamFinish(${shape.in}); read = $framesRead; written = $framesWritten")
      onPull()
    }
  }
}