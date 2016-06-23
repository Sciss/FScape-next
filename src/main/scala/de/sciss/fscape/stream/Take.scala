/*
 *  Take.scala
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
import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.graph.ConstantI
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn2DImpl, StageImpl, StageLogicImpl}

object Take {
  def head(in: OutD)(implicit b: Builder): OutD = {
    val len = ConstantI(1).toInt
    apply(in = in, len = len)
  }

  def apply(in: OutD, len: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in , stage.in0)
    b.connect(len, stage.in1)
    stage.out
  }

  private final val name = "Take"

  private type Shape = FanInShape2[BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in" ),
      in1 = InI (s"$name.len"),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with ChunkImpl[Shape]
      with FilterIn2DImpl[BufD, BufI] {

    private[this] var takeRemain    = Int.MaxValue
    private[this] var init          = true

    protected def shouldComplete(): Boolean = (takeRemain == 0) || (inRemain == 0 && isClosed(in0))

//    var LAST_BUF: BufD = null

    protected def processChunk(): Boolean = {
//      if (LAST_BUF != bufOut0) {
//        LAST_BUF = bufOut0
//        println(s"take   : ${bufOut0.hashCode.toHexString} - ${bufOut0.buf.toVector.hashCode.toHexString}")
//      }

      val len = math.min(inRemain, outRemain)
      val res = len > 0
      if (res) {
        if (init) {
          takeRemain = math.max(0, bufIn1.buf(0))
          init = false
        }
        val chunk = math.min(len, takeRemain)
        if (chunk > 0) {
          Util.copy(bufIn0.buf, inOff, bufOut0.buf, outOff, chunk)
          takeRemain -=chunk
          outOff     += chunk
          outRemain  -= chunk
        }
        inOff    += len
        inRemain -= len
      }
      res
    }
  }
}