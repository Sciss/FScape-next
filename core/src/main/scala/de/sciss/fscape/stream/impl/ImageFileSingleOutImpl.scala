package de.sciss.fscape.stream.impl

import akka.stream.Shape
import de.sciss.fscape.logStream

trait ImageFileSingleOutImpl[S <: Shape] extends ImageFileOutImpl[S] {
  logic: NodeImpl[S] =>

  // ---- abstract ----

  protected def specReady: Boolean

  // ---- impl ----

  private[this] var _canReadImg = false

  protected final def canReadImage: Boolean = _canReadImg

  /** Called when all of `inlets1` are ready. */
  protected def processImg(): Unit = {
    _canReadImg = true
    if (specReady) {
      process()
    }
  }

  private def process(): Unit = {
    val chunk = readImgInlets()
    if (chunk > 0) {
      processChunk(inOff = 0, chunk = chunk)
    }
    if (framesWritten == numFrames) {
      logStream(s"completeStage() $this")
      completeStage()
    }
  }
}
