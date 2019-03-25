/*
 *  ImageFileSeqOutImpl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.Shape
import akka.stream.stage.InHandler
import de.sciss.file.File
import de.sciss.fscape.graph.ImageFileSeqIn.formatTemplate
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.{BufI, InI}

trait ImageFileSeqOutImpl[S <: Shape] extends ImageFileOutImpl[S] {
  logic: NodeImpl[S] =>

  // ---- abstract ----

  protected def template: File

  protected def inletIndices: InI

  protected def specReady: Boolean

  // ---- impl ----

  private[this] var bufInIndices: BufI = _

  private[this] var _canReadIndices = false
  private[this] var _inValidIndices = false

  private[this] var _canReadImg     = false

  private[this] var inOffIndices    = 0
  private[this] var inRemainIndices = 0
  private[this] var framesRemain    = 0

  private[this] var inOffImg        = 0
  private[this] var inRemainImg     = 0

  // ---- handlers ----

  private object IndicesHandler extends InHandler {
    def onPush(): Unit = if (isInitialized) {
      logStream(s"onPush($inletIndices)")
      testRead()
    }

    def checkPushed(): Unit =
      if (isAvailable(inletIndices)) {
        onPush()
      }

    private def testRead(): Unit = {
      updateCanReadIndices()
      if (_canReadIndices && specReady) process()
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish($inletIndices)")
      if (_inValidIndices || isAvailable(inletIndices)) {
        testRead()
      } else {
        logStream(s"Invalid aux $inletIndices")
        completeStage()
      }
    }
  }

  protected final def setIndicesHandler(): Unit =
    setHandler(inletIndices, IndicesHandler)

  protected final def checkIndicesPushed(): Unit =
    IndicesHandler.checkPushed()

  // ----

  private def inputsEndedIndices: Boolean = inRemainIndices == 0 && isClosed(inletIndices) && !isAvailable(inletIndices)

  @inline
  private[this] def shouldReadIndices = inRemainIndices == 0 && _canReadIndices

  @inline
  private[this] def shouldReadImg = inRemainImg == 0 && _canReadImg

  protected final def canReadImage: Boolean = _canReadImg

  private def readInIndices(): Int = {
    freeBufferInIndices()
    bufInIndices = grab(inletIndices)
    tryPull(inletIndices)

    _inValidIndices = true
    updateCanReadIndices()
    bufInIndices.size
  }

  private def updateCanReadIndices(): Unit =
    _canReadIndices = isAvailable(inletIndices)

  @inline
  private[this] def freeBufferInIndices(): Unit =
    if (bufInIndices != null) {
      bufInIndices.release()
      bufInIndices = null
    }

  /** Called when all of `inlets1` are ready. */
  protected def processImg(): Unit = {
    _canReadImg = true
    if (specReady) {
      process()
    }
  }

  private def process(): Unit = {
    logStream(s"process() $this")
    var stateChange = false

    if (shouldReadIndices) {
      inRemainIndices = readInIndices()
      inOffIndices    = 0
      stateChange     = true
    }

    if (framesRemain == 0 && inRemainIndices > 0) {
      val f = formatTemplate(template, bufInIndices.buf(inOffIndices))
      openImage(f)
      framesRemain      = numFrames
      inOffIndices     += 1
      inRemainIndices  -= 1
      stateChange       = true
    }

    if (shouldReadImg) {
      inRemainImg   = readImgInlets()
      inOffImg      = 0
      _canReadImg   = false
      stateChange   = true
    }

    val chunk = math.min(inRemainImg, framesRemain)

    if (chunk > 0) {
      processChunk(inOff = inOffImg, chunk = chunk)
      inOffImg     += chunk
      inRemainImg  -= chunk
      framesRemain -= chunk
      stateChange   = true
    }

    val done = framesRemain == 0 && inputsEndedIndices

    if (done) {
      logStream(s"completeStage() $this")
      completeStage()
      stateChange = false
    }

    if (stateChange) process()
  }
}
