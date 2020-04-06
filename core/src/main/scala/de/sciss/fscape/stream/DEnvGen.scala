/*
 *  DEnvGen.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.{switch, tailrec}

object DEnvGen {
  def apply(levels: OutD, lengths: OutL, shapes: OutI, curvatures: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(levels    , stage.in0)
    b.connect(lengths   , stage.in1)
    b.connect(shapes    , stage.in2)
    b.connect(curvatures, stage.in3)
    stage.out
  }

  private final val name = "DEnvGen"

  private type Shp = FanInShape4[BufD, BufL, BufI, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.levels"),
      in1 = InL (s"$name.lengths"),
      in2 = InI (s"$name.shapes"),
      in3 = InD (s"$name.curvatures"),
      out = OutD(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hLevel  = Handlers.InDMain  (this, shape.in0)
    private[this] val hLen    = Handlers.InLAux   (this, shape.in1)(math.max(0L, _))
    private[this] val hShape  = Handlers.InIAux   (this, shape.in2)()
    private[this] val hCurve  = Handlers.InDAux   (this, shape.in3)()
    private[this] val hOut    = Handlers.OutDMain (this, shape.out)

    private[this] var startLevel  : Double  = _
    private[this] var endLevel    : Double  = _
    private[this] var period      : Long    = _
    private[this] var shapeId     : Int     = _
    private[this] var curvature   : Double  = _

    private[this] var period1     : Long    = _
    private[this] var shapeId1    : Int     = _
    private[this] var curvature1  : Double  = _

    private[this] var init          = true
    private[this] var nextSegment   = true
    private[this] var offSeg        = 0L    // time counter within each segment (until period)

    private def shouldComplete(): Boolean =
      nextSegment && hLevel.isDone

    @tailrec
    protected def process(): Unit = {
      val active = processChunk()
      if (shouldComplete()) {
        if (hOut.flush()) completeStage()
      } else if (active) process()
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    private def pullShapeParams(): Unit = {
      period1     = hLen  .next()
      shapeId1    = hShape.next()
      curvature1  = hCurve.next()
    }

    private def canPullParams: Boolean =
      hLevel.hasNext && hLen.hasNext && hShape.hasNext && hCurve.hasNext

    /* Should read and possibly update `inRemain`, `outRemain`, `inOff`, `outOff`.
     *
     * @return `true` if this method did any actual processing.
     */
    private def processChunk(): Boolean = {
      var stateChange = false

      if (init) {
        if (!canPullParams) return stateChange

        endLevel    = hLevel.next()
        pullShapeParams()
        init        = false
        stateChange = true
      }

      if (nextSegment && canPullParams) {
        startLevel  = endLevel
        endLevel    = hLevel.next()
        // "delay1"
        period      = period1
        shapeId     = shapeId1
        curvature   = curvature1
        pullShapeParams()
        stateChange = true
        nextSegment = period == 0L
      }

      if (!nextSegment) {
        var offSegI = offSeg
        val periodI = period
        val chunk   = math.min(hOut.available, periodI - offSegI).toInt
        if (chunk > 0) {
          var outOffI = hOut.offset
          val stop    = outOffI + chunk
          val y1      = startLevel
          val y2      = endLevel
          val out     = hOut.array

          (shapeId: @switch) match {
            case 0 /* step */ =>
              val v = y1
              while (outOffI < stop) {
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 1 /* linear */ =>
              val dy = y2 - y1
              while (outOffI < stop) {
                val pos = offSegI.toDouble / periodI
                val v   = pos * dy + y1
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 2 /* exponential */ =>
              val y1Lim = math.max(0.0001, y1)
              val fy    = y2 / y1Lim
              while (outOffI < stop) {
                val pos   = offSegI.toDouble / periodI
                val v     = y1Lim * math.pow(fy, pos)
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 3 /* sine */ =>
              val dy          = y2 - y1
              val piByPeriod  = math.Pi / periodI
              while (outOffI < stop) {
                val posP  = offSegI.toDouble * piByPeriod
                val v     = y1 + dy * (-math.cos(posP) * 0.5 + 0.5)
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 4 /* welch */ =>
              val isUp    = y1 < y2
              val posMul  = if (isUp) math.Pi * 0.5 else math.Pi * -0.5
              val posAdd  = if (isUp) 0.0 else -1.0
              while (outOffI < stop) {
                val pos   = offSegI.toDouble / periodI
                val v     = y1 + (y2 - y1) * math.sin((pos + posAdd) * posMul)
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 5 /* parametric */ =>
              val curveI    = curvature
              val dy        = y2 - y1
              val isLin     = math.abs(curveI) < 0.00001
              val denom     = if (isLin) 0.0 else 1.0 - math.exp(curveI)
              while (outOffI < stop) {
                val pos   = offSegI.toDouble / periodI
                val v     = if (isLin) {  // effectively linear
                  pos * dy + y1
                } else {
                  val num = 1.0 - math.exp(pos * curveI)
                  y1 + dy * (num / denom)
                }
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 6 /* squared */ =>
              val y1Pow2  = math.sqrt(y1)
              val y2Pow2  = math.sqrt(y2)
              val dyP     = y2Pow2 - y1Pow2
              while (outOffI < stop) {
                val pos   = offSegI.toDouble / periodI
                val yPow2   = pos * dyP + y1Pow2
                val v       = yPow2 * yPow2
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }

            case 7 /* cubed */ =>
              val y1Pow3  = math.pow(y1, 0.3333333)
              val y2Pow3  = math.pow(y2, 0.3333333)
              val dyP     = y2Pow3 - y1Pow3
              while (outOffI < stop) {
                val pos   = offSegI.toDouble / periodI
                val yPow3   = pos * dyP + y1Pow3
                val v       = yPow3 * yPow3 * yPow3
                out(outOffI) = v
                outOffI += 1
                offSegI += 1
              }
          }
          hOut.advance(chunk)

          if (offSegI == periodI) {
            nextSegment = true
            offSeg      = 0
          } else {
            offSeg      = offSegI
          }

          stateChange = true
        }
      }

      stateChange
    }
  }
}