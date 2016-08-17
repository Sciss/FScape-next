/*
 *  Resample.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn6DImpl, FilterLogicImpl, StageImpl, StageLogicImpl}

object Resample {
  def apply(in: OutD, factor: OutD, minFactor: OutD, rollOff: OutD, kaiserBeta: OutD, zeroCrossings: OutI)
           (implicit b: Builder): OutD = {
    val stage0  = new Stage
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

  private type Shape = FanInShape6[BufD, BufD, BufD, BufD, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"           ),
      in1 = InD (s"$name.factor"       ),
      in2 = InD (s"$name.minFactor"    ),
      in3 = InD (s"$name.rollOff"      ),
      in4 = InD (s"$name.kaiserBeta"   ),
      in5 = InI (s"$name.zeroCrossings"),
      out = OutD(s"$name.out"          )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with ChunkImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn6DImpl[BufD, BufD, BufD, BufD, BufD, BufI] {

    protected def shouldComplete(): Boolean = ???
    
    private[this] var rollOff       = -1.0
    private[this] var kaiserBeta    = -1.0
    private[this] var zeroCrossings = -1

    /** Should read and possibly update `inRemain`, `outRemain`, `inOff`, `outOff`.
      *
      * @return `true` if this method did any actual processing.
      */
    protected def processChunk(): Boolean = {
      var newTable = false

      val inOffI = inOff

      if (bufIn3 != null && inOffI < bufIn3.size) {
        val newRollOff = math.max(0.0, math.min(1.0, bufIn3.buf(inOffI)))
        if (rollOff != newRollOff) {
          rollOff   = newRollOff
          newTable  = true
        }
      }

      if (bufIn4 != null && inOffI < bufIn4.size) {
        val newKaiserBeta = math.max(0.0, bufIn4.buf(inOffI))
        if (kaiserBeta != newKaiserBeta) {
          kaiserBeta  = newKaiserBeta
          newTable    = true
        }
      }

      if (bufIn5 != null && inOffI < bufIn5.size) {
        val newZeroCrossings = math.max(1, bufIn5.buf(inOffI))
        if (zeroCrossings != newZeroCrossings) {
          zeroCrossings = newZeroCrossings
          newTable      = true
        }
      }
      
      ???
    }
  }
}