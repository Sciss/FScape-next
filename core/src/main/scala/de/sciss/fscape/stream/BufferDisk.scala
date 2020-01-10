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
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeHasInitImpl, NodeImpl}

// XXX TODO --- we could use a "quasi-circular"
// structure? this is, overwrite parts of the file
// that were already read
object BufferDisk {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "BufferDisk"

  private type Shape = FlowShape[BufD, BufD]

  private final class Stage(layer: Layer)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](name) {

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) with NodeHasInitImpl with InHandler with OutHandler {

    private[this] var af: FileBuffer  = _
    private[this] val bufSize       = ctrl.blockSize
    private[this] var buf           = new Array[Double](bufSize)

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
      buf = null
      if (af != null) {
        af.dispose()
        af = null
      }
    }

    def onPush(): Unit = {
      val bufIn = grab(shape.in)
      tryPull(shape.in)
      val chunk = bufIn.size
      logStream(s"onPush(${shape.in}) $chunk; read = $framesRead; written = $framesWritten")

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
      logStream(s"onPull(${shape.out}) $chunk; read = $framesRead; written = $framesWritten")
      if (chunk == 0) {
        if (inputDone) {
          logStream(s"completeStage() $this")
          completeStage()
        }

      } else {
        if (af.position != framesRead) af.position = framesRead
        val bufOut = ctrl.borrowBufD()
        af.read(bufOut.buf, 0, chunk)
        framesRead += chunk
        bufOut.size = chunk
        push(shape.out, bufOut)
      }
    }

    // in closed
    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish(${shape.in}); read = $framesRead; written = $framesWritten")
      onPull()
    }
  }
}