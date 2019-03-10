/*
 *  ImageFileSeqOut.scala
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

package de.sciss.fscape
package stream

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile.Spec
import de.sciss.fscape.stream.impl.{BlockingGraphStage, ImageFileOutImpl, In1UniformSinkShape, NodeImpl}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

object ImageFileSeqOut {
  def apply(template: File, spec: Spec, indices: OutI, in: ISeq[OutD])(implicit b: Builder): Unit = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(layer = b.layer, template = template, spec = spec)
    val stage = b.add(sink)
    b.connect(indices, stage.in0)
    (in zip stage.inlets1).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "ImageFileSeqOut"

  private type Shape = In1UniformSinkShape[BufI, BufD]

  private final class Stage(layer: Layer, template: File, spec: Spec)(implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${template.name})") {

    val shape: Shape = In1UniformSinkShape[BufI, BufD](
      InI(s"$name.indices"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer = layer, template = template, spec = spec)
  }

  private final class Logic(shape: Shape, layer: Layer, template: File, val spec: Spec)(implicit ctrl: Control)
    extends NodeImpl(s"$name(${template.name})", layer, shape)
    with ImageFileOutImpl[Shape] { logic =>

    protected val inlets1: Vec[InD] = shape.inlets1.toIndexedSeq
    private[this] val in0 = shape.in0

    private[this] var bufIn0: BufI = _

    private[this] var _canRead0     = false
    private[this] var _inValid0     = false

    private[this] var _canRead1     = false
//    private[this] var _inValid1     = false

    private[this] var inOff0        = 0
    private[this] var inRemain0     = 0
    private[this] var framesRemain  = 0

    private[this] var inOff1        = 0
    private[this] var inRemain1     = 0

    // ---- set handlers ----

    shape.inlets1.foreach(setHandler(_, this))

    setHandler(in0, new InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in0)")
        testRead()
      }

      private def testRead(): Unit = {
        updateCanRead0()
        if (_canRead0) process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in0)")
        if (_inValid0 || isAvailable(in0)) {
          testRead()
        } else {
          logStream(s"Invalid aux $in0")
          completeStage()
        }
      }
    })

    // ----

    private def inputsEnded0: Boolean = inRemain0 == 0 && isClosed(in0) && !isAvailable(in0)

    @inline
    private[this] def shouldRead0 = inRemain0 == 0 && _canRead0

    @inline
    private[this] def shouldRead1 = inRemain1 == 0 && _canRead1

    private def readIns0(): Int = {
      freeInputBuffer0()
      bufIn0 = grab(in0)
      tryPull(in0)

      _inValid0 = true
      updateCanRead0()
      bufIn0.size
    }

    private def updateCanRead0(): Unit =
      _canRead0 = isAvailable(in0)

    @inline
    private[this] def freeInputBuffer0(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    /** Called when all of `inlets1` are ready. */
    protected def process1(): Unit = {
      _canRead1 = true
      process()
    }

    private def process(): Unit = {
      logStream(s"process() $this")
      var stateChange = false

      if (shouldRead0) {
        inRemain0   = readIns0()
        inOff0      = 0
        stateChange = true
      }

      if (framesRemain == 0 && inRemain0 > 0) {
        val name      = template.name.format(bufIn0.buf(inOff0))
        val f         = template.parentOption.fold(file(name))(_ / name)
        openImage(f)
        framesRemain  = numFrames
        inOff0       += 1
        inRemain0    -= 1
        stateChange   = true
      }

      if (shouldRead1) {
        inRemain1     = readIns1()
        inOff1        = 0
        _canRead1     = false
        stateChange   = true
      }

      val chunk = math.min(inRemain1, framesRemain)

      if (chunk > 0) {
        processChunk(inOff = inOff1, chunk = chunk)
        inOff1       += chunk
        inRemain1    -= chunk
        framesRemain -= chunk
        stateChange   = true
      }

      val done = framesRemain == 0 && inputsEnded0

      if (done) {
        logStream(s"completeStage() $this")
        completeStage()
        stateChange = false
      }

      if (stateChange) process()
    }
  }
}