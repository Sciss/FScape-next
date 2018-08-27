/*
 *  WindowIndexWhere.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterIn2Impl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object WindowIndexWhere {
  def apply(p: OutI, size: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(p   , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "WindowIndexWhere"

  private type Shape = FanInShape2[BufI, BufI, BufI]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InI (s"$name.p"   ),
      in1 = InI (s"$name.size"),
      out = OutI(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufI, Shape]
      with FilterIn2Impl[BufI, BufI, BufI] {

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

    private[this] var size  : Int = _
    private[this] var index : Int = _

    protected def startNextWindow(inOff: Int): Long = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      index = -1
      size
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      if (index < 0) {
        val b = bufIn0.buf
        var i = inOff
        val s = i + chunk
        while (i < s) {
          if (b(i) != 0) {
            index = writeToWinOff.toInt + (i - inOff)
            return
          }
          i += 1
        }
      }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      assert(readFromWinOff == 0 && chunk == 1)
      bufOut0.buf(outOff) = index
    }

    protected def processWindow(writeToWinOff: Long): Long = 1
  }
}