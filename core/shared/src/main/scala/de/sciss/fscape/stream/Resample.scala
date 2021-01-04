/*
 *  Resample.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.{NodeImpl, ResampleImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.math.max

object Resample {
  def apply(in: OutD, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in           , stage.in0)
    b.connect(factor       , stage.in1)
    b.connect(minFactor    , stage.in2)
    b.connect(rollOff      , stage.in3)
    b.connect(kaiserBeta   , stage.in4)
    b.connect(zeroCrossings, stage.in5)
    stage.out
  }

  private final val name = "Resample"

  private type Shp = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape6(
      in0 = InD (s"$name.in"           ),
      in1 = InD (s"$name.factor"       ),
      in2 = InD (s"$name.minFactor"    ),
      in3 = InD (s"$name.rollOff"      ),
      in4 = InD (s"$name.kaiserBeta"   ),
      in5 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends ResampleImpl(name, layer, shape) {

    // rather arbitrary, but > 1 increases speed; for matrix resample, we'd want very small to save memory
    // N.B.: there is a bug (#37) that has to do with this value. Still investigating; 8 seems safe
    protected val PAD: Int = ctrl.blockSize // 8 // 32

    private[this] var winBuf: Array[Double] = _   // circular

    // ---- start/stop ----

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf  = null
    }

    // ---- infra ----

    protected val hIn            : InDMain  = InDMain (this, shape.in0)
    protected val hFactor        : InDAux   = InDAux  (this, shape.in1)(max(0.0, _))
    protected val hMinFactor     : InDAux   = InDAux  (this, shape.in2)(max(0.0, _))
    protected val hRollOff       : InDAux   = InDAux  (this, shape.in3)(_.clip(0.0, 1.0))
    protected val hKaiserBeta    : InDAux   = InDAux  (this, shape.in4)(max(1.0, _))
    protected val hZeroCrossings : InIAux   = InIAux  (this, shape.in5)(max(1, _))
    protected val hOut           : OutDMain = OutDMain(this, shape.out)

    // ---- process ----

    protected def processChunk(): Boolean = resample()

    protected def allocWinBuf(len: Int): Unit =
      winBuf = new Array[Double](len)

    protected def clearWinBuf(off: Int, len: Int): Unit =
      Util.clear(winBuf, off, len)

    protected def copyInToWinBuf(winOff: Int, len: Int): Unit = {
      hIn.nextN(winBuf, winOff, len)
    }

    protected def availableInFrames : Int = hIn .available
    protected def availableOutFrames: Int = hOut.available

    private[this] var value = 0.0

    protected def clearValue(): Unit =
      value = 0.0

    protected def addToValue(winOff: Int, weight: Double): Unit =
      value += winBuf(winOff) * weight

    protected def copyValueToOut(): Unit = {
      hOut.next(value * gain)
    }
  }
}