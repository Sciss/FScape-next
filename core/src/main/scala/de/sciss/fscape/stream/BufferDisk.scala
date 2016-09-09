/*
 *  BufferDisk.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{BlockingGraphStage, StageLogicImpl}

import scala.util.control.NonFatal

// XXX TODO --- we could use a "quasi-circular"
// structure? this is, overwrite parts of the file
// that were already read
object BufferDisk {
  def apply(in: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "BufferDisk"

  private type Shape = FlowShape[BufD, BufD]

  private final class Stage(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](name) {

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: FlowShape[BufD, BufD])(implicit ctrl: Control)
    extends StageLogicImpl(name, shape) with InHandler with OutHandler {

    private[this] var af: FileBuffer  = _
    private[this] val bufSize       = ctrl.blockSize
    private[this] var buf           = new Array[Double](bufSize)

    private[this] var framesWritten = 0L
    private[this] var framesRead    = 0L

    setHandlers(shape.in, shape.out, this)

    override def preStart(): Unit = {
      af = FileBuffer()
      pull(shape.in)
    }

    override def postStop(): Unit = {
      buf = null
      try {
        af.dispose()
        af = null
      } catch {
        case NonFatal(ex) =>  // XXX TODO -- what with this?
      }
    }

    def onPush(): Unit = {
      val bufIn = grab(shape.in)
      tryPull(shape.in)
      val chunk = bufIn.size
      logStream(s"onPush(${shape.in}) $chunk")

      try {
        if (af.position != framesWritten) af.position = framesWritten
        af.write(bufIn.buf, 0, chunk)
        framesWritten += chunk
        // logStream(s"framesWritten = $framesWritten")
      } finally {
        bufIn.release()
      }

      if (isAvailable(shape.out)) onPull()
    }

    def onPull(): Unit = {
      val chunk = math.min(bufSize, framesWritten - framesRead).toInt
      logStream(s"onPull(${shape.out}) $chunk")
      if (chunk == 0) {
        if (isClosed(shape.in)) {
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
      logStream(s"onUpstreamFinish(${shape.in}")
      if (isAvailable(shape.out)) onPull()
    }
  }
}