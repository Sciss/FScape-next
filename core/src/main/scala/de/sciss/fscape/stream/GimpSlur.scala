/*
 *  GimpSlur.scala
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

import java.util

import akka.stream.{Attributes, FanInShape8, Inlet}
import de.sciss.fscape.stream.impl.Handlers.{InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.numbers.{IntFunctions => ri}

import scala.annotation.tailrec
import scala.math.{max, min}
import scala.util.Random

object GimpSlur {
  def apply(in: OutD, width: OutI, height: OutI, kernel: OutD, kernelWidth: OutI, kernelHeight: OutI,
            repeat: OutI, wrap: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in          , stage.in0)
    b.connect(width       , stage.in1)
    b.connect(height      , stage.in2)
    b.connect(kernel      , stage.in3)
    b.connect(kernelWidth , stage.in4)
    b.connect(kernelHeight, stage.in5)
    b.connect(repeat      , stage.in6)
    b.connect(wrap        , stage.in7)
    stage.out
  }

  private final val name = "GimpSlur"

  private type Shp = FanInShape8[BufD, BufI, BufI, BufD, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape8(
      in0 = InD (s"$name.in"          ),
      in1 = InI (s"$name.width"       ),
      in2 = InI (s"$name.height"      ),
      in3 = InD (s"$name.kernel"      ),
      in4 = InI (s"$name.kernelWidth" ),
      in5 = InI (s"$name.kernelHeight"),
      in6 = InI (s"$name.repeat"      ),
      in7 = InI (s"$name.wrap"        ),
      out = OutD(s"$name.out"         )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hIn           = InDMain   (this, shape.in0)
    private[this] val hOut          = OutDMain  (this, shape.out)
    private[this] val hWidth        = InIAux    (this, shape.in1)(max(1, _))
    private[this] val hHeight       = InIAux    (this, shape.in2)(max(1, _))
    private[this] val hKernel       = InDMain   (this, shape.in3)
    private[this] val hKernelWidth  = InIAux    (this, shape.in4)(max(1, _))
    private[this] val hKernelHeight = InIAux    (this, shape.in5)(max(1, _))
    private[this] val hRepeat       = InIAux    (this, shape.in6)(max(1, _))
    private[this] val hWrap         = InIAux    (this, shape.in7)()

    private[this] var width       : Int     = _
    private[this] var height      : Int     = _
    private[this] var kernelWidth : Int     = -1
    private[this] var kernelHeight: Int     = -1
    private[this] var repeat      : Int     = _
    private[this] var wrap        : Boolean = _

    private[this] val rnd: Random = ctrl.mkRandom()

    private[this] var kernelBuf : Array[Double] = _
    private[this] var kernelSize  : Int     = _
    private[this] var hasKernel         = false
    private[this] var kernelRemain      = 0
    private[this] var kernelOff         = 0

    private[this] var imageInBuf    : Array[Double] = _
    private[this] var imageOutBuf   : Array[Double] = _
    private[this] var imageSize = -1
    private[this] var imageRemain      = 0
    private[this] var imageOff         = 0

    private[this] var state = 0   // 0 = gather frame parameters, 1 = gather kernel and input frame, 2 = write frame

    override protected def stopped(): Unit = {
      super.stopped()
      imageInBuf  = null
      imageOutBuf = null
      kernelBuf   = null
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (inlet == hIn.inlet) {
        if (state == 1) {
          if (imageOff == 0) {
            if (hOut.flush()) completeStage()
          } else {
            process()
          }
        }
      } else {
        assert (inlet == hKernel.inlet)
        if (state == 1) process()
      }

    @tailrec
    protected def process(): Unit = {
      if (state == 0) {
        if (!startNextFrame()) return
        state = 1
      }

      if (state == 1) {
        // ---- kernel ----
        if (hKernel.isDone) {
          if (kernelOff == 0) { // haven't started a new kernel yet
            if (hasKernel) {    // reuse previous kernel
              kernelOff     = kernelSize
              kernelRemain  = 0
            } else {            // do not have existing kernel; nothing we can do than close down
              if (hOut.flush()) completeStage()
              return
            }

          } else {  // we started a new kernel; complete it with zero-padding
            if (kernelRemain > 0) clearKernelTail()
          }

        } else {  // kernel inlet still active; try to advance
          readKernel()
          if (hKernel.isDone && kernelRemain > 0) clearKernelTail()
        }

        // ---- input frame ----
        readInputToFrame()
        if (hIn.isDone && imageRemain > 0) clearImageTail()

        if (kernelRemain > 0 || imageRemain > 0) return
        hasKernel = true

        renderFrame()
        imageOff    = 0
        imageRemain = imageSize
        state       = 2
      }

      writeOutputFromFrame()
      if (imageRemain == 0) {
        // println(s"frame done $this. hIn.isDone = ${hIn.isDone}")
        if (hIn.isDone & hOut.flush()) completeStage()
        else {
          state = 0
          process()
        }
      // } else {
      //   println(s"imageRemain $imageRemain for $this")
      }
    }

    private def clearKernelTail(): Unit = {
      Util.clear(kernelBuf, kernelOff, kernelRemain)
      kernelOff     = kernelSize
      kernelRemain  = 0
    }

    private def clearImageTail(): Unit = {
      Util.clear(imageInBuf, imageOff, imageRemain)
      imageOff    = kernelSize
      imageRemain = 0
    }

    private def startNextFrame(): Boolean = {
      val ok = hWidth.hasNext && hHeight.hasNext && hKernelWidth.hasNext && hKernelHeight.hasNext &&
        hRepeat.hasNext && hWrap.hasNext
      if (ok) {
        val oldKernelW  = kernelWidth
        val oldKernelH  = kernelHeight

        width         = hWidth        .next()
        height        = hHeight       .next()
        kernelWidth   = hKernelWidth  .next()
        kernelHeight  = hKernelHeight .next()
        repeat        = hRepeat       .next()
        wrap          = hWrap         .next() > 0

        val needsKernel = if (kernelWidth != oldKernelW || kernelHeight != oldKernelH) {
          kernelSize    = kernelWidth * kernelHeight
          kernelBuf     = new Array[Double](kernelSize)
          hasKernel     = false
          true

        } else {
          !hasKernel && !hKernel.isDone
        }

        kernelRemain  = if (needsKernel) kernelSize else 0
        kernelOff     = 0

        val newSizeOuter = width * height
        if (newSizeOuter != imageSize) {
          imageSize   = newSizeOuter
          imageInBuf     = new Array(newSizeOuter)
          imageOutBuf     = new Array(newSizeOuter)
        }

        imageRemain   = imageSize
        imageOff      = 0
      }
      ok
    }

    @tailrec
    private def readKernel(): Unit = {
      val rem = min(kernelRemain, hKernel.available)
      if (rem == 0) return

      hKernel.nextN(kernelBuf, kernelOff, rem)
      kernelOff    += rem
      kernelRemain -= rem
      readKernel()
    }

    @tailrec
    private def readInputToFrame(): Unit = {
      val rem = min(imageRemain, hIn.available)
      if (rem == 0) return

      hIn.nextN(imageInBuf, imageOff, rem)
      imageOff    += rem
      imageRemain -= rem
      readInputToFrame()
    }

    private def writeOutputFromFrame(): Unit = {
      val rem = min(imageRemain, hOut.available)
      if (rem == 0) return

      hOut.nextN(imageOutBuf, imageOff, rem)
      imageOff    += rem
      imageRemain -= rem
    }

    private def renderFrame(): Unit = {
      val w       = width
      val h       = height
      val wk      = kernelWidth
      val hk      = kernelHeight
      val cx      = wk >>> 1
      val cy      = hk >>> 1
      val szKern  = kernelSize
      var bIn     = imageInBuf
      var bOut    = imageOutBuf
      val bKern   = kernelBuf
      val _wrap   = wrap

      var r = repeat
      while (r > 0) {
        var off = 0
        var y = 0
        while (y < h) {
          var x = 0
          while (x < w) {
            val n   = rnd.nextDouble()
            val i   = util.Arrays.binarySearch(bKern, 0, szKern, n)
            val j   = if (i >= 0) i else math.min(szKern - 1, -(i + 1))
            var xi  = (j % wk) - cx + x
            var yi  = (j / wk) - cy + y

            if (xi < 0 || xi >= w) {
              xi = if (_wrap) ri.wrap(xi, 0, w - 1) else ri.clip(xi, 0, w - 1)
            }
            if (yi < 0 || yi >= h) {
              yi = if (_wrap) ri.wrap(yi, 0, h - 1) else ri.clip(yi, 0, h - 1)
            }
            val value = bIn(yi * w + xi)
            bOut(off) = value

            off += 1
            x   += 1
          }
          y += 1
        }

        val tmp = bIn
        bIn     = bOut
        bOut    = tmp
        r -= 1
      }

      // now make sure `imageOutBuf` points to the right thing (swap if repeats is even)
      if (repeat % 2 == 0) {
        val tmp = imageInBuf
        imageInBuf  = imageOutBuf
        imageOutBuf = tmp
      }
    }
  }
}