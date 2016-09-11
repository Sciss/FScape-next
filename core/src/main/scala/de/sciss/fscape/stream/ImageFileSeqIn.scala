/*
 *  ImageFileSeqIn.scala
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

import akka.stream.stage.InHandler
import akka.stream.{Attributes, UniformFanOutShape}
import de.sciss.file._
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileInImpl, NodeImpl}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

/*
  XXX TODO: use something like ImgLib2 that supports high resolution images:
  http://imagej.net/ImgLib2_Examples#Example_1_-_Opening.2C_creating_and_displaying_images
 */
object ImageFileSeqIn {
  def apply(template: File, numChannels: Int, indices: OutI)(implicit b: Builder): Vec[OutD] = {
    val source  = new Stage(template, numChannels = numChannels)
    val stage   = b.add(source)
    b.connect(indices, stage.in)
    stage.outArray.toIndexedSeq
  }

  private final val name = "ImageFileSeqIn"

  private type Shape = UniformFanOutShape[BufI, BufD]

  // similar to internal `UnfoldResourceSource`
  private final class Stage(template: File, numChannels: Int)(implicit ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${template.name})") {

    val shape: Shape = UniformFanOutShape(
      inlet   = InI(s"$name.indices"),
      outlets = Vector.tabulate(numChannels)(ch => OutD(s"$name.out$ch")): _*
    )

    def createLogic(attr: Attributes) = new Logic(shape, template, numChannels = numChannels)
  }

  private final class Logic(shape: Shape, template: File, protected val numChannels: Int)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${template.name})", shape)
    with ImageFileInImpl[Shape]
    with InHandler {

    protected val outlets: Vec[OutD] = shape.outArray.toIndexedSeq

    private[this] val in0 = shape.in

    private[this] var bufIn0: BufI = _

    private[this] var _canRead      = false
    private[this] var _inValid      = false
    private[this] var inOff         = 0
    private[this] var inRemain      = 0
    private[this] var outOff        = 0
    private[this] var outRemain     = ctrl.blockSize
    private[this] var framesRemain  = 0

    shape.outlets.foreach(setHandler(_, this))
    setHandler(in0, this)

    override def preStart(): Unit = {
      logStream(s"preStart() $this")
      pull(in0)
    }

    private def inputsEnded: Boolean = inRemain == 0 && isClosed(in0)

    @inline
    private[this] def shouldRead = inRemain == 0 && _canRead

    @tailrec
    protected def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (shouldRead) {
        inRemain    = readIns()
        inOff       = 0
        stateChange = true
      }

      if (framesRemain == 0 && inRemain > 0) {
        val name      = template.name.format(bufIn0.buf(inOff))
        val f         = template.parentOption.fold(file(name))(_ / name)
        openImage(f)
        framesRemain  = numFrames
        inOff        += 1
        inRemain     -= 1
        stateChange   = true
      }

      val chunk = math.min(outRemain, framesRemain)

      if (chunk > 0) {
        processChunk(outOff = outOff, chunk = chunk)
        outOff       += chunk
        outRemain    -= chunk
        framesRemain -= chunk
        stateChange   = true
      }

      val flushOut = framesRemain == 0 && inputsEnded

      if ((outRemain == 0 || flushOut) && canWrite) {
        if (outOff > 0) {
          writeOuts(outOff)
          outOff      = 0
          outRemain   = ctrl.blockSize
          stateChange = true
        }

        if (flushOut) {
          logStream(s"completeStage() $this")
          completeStage()
          stateChange = false
        }
      }

      if (stateChange) process()
    }

    private def updateCanRead(): Unit =
      _canRead = isAvailable(in0)

    private def readIns(): Int = {
      freeInputBuffers()
      val sh = shape
//      if (isAvailable(sh.in)) {
        bufIn0 = grab(sh.in)
        tryPull(sh.in)
//      }

      _inValid = true
      updateCanRead()
      bufIn0.size
    }

    protected def freeInputBuffers(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    // ---- InHandler ----

    def onPush(): Unit = {
      logStream(s"onPush($in0)")
      testRead()
    }

    private def testRead(): Unit = {
      updateCanRead()
      if (_canRead) process()
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"onUpstreamFinish($in0)")
      if (_inValid || isAvailable(in0)) {
        testRead()
      } else {
        println(s"Invalid aux $in0")
        completeStage()
      }
    }
  }
}