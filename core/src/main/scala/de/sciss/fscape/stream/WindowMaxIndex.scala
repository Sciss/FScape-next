/*
 *  WindowMaxIndex.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{FilterIn2Impl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

object WindowMaxIndex {
  def apply(in: OutD, size: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "WindowMaxIndex"

  private type Shape = FanInShape2[BufD, BufI, BufI]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape2(
      in0 = InD (s"$name.in"  ),
      in1 = InI (s"$name.size"),
      out = OutI(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn2Impl[BufD, BufI, BufI] {

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

    private[this] var size    : Int     = _
    private[this] var index   : Int     = _
    private[this] var maxValue: Double  = _

    protected def startNextWindow(inOff: Int): Long = {
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      index     = 0
      maxValue  = Double.NegativeInfinity
      size
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit = {
      val b       = bufIn0.buf
      var i       = inOff
      val s       = i + chunk
      var _max    = maxValue
      var _index  = index
      val d       = writeToWinOff.toInt - inOff
      while (i < s) {
        val v = b(i)
        if (v > _max) {
          _max    = v
          _index  = i + d
        }
        i += 1
      }
      maxValue  = _max
      index     = _index
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      assert(readFromWinOff == 0 && chunk == 1)
      bufOut0.buf(outOff) = index
    }

    protected def processWindow(writeToWinOff: Long): Long = 1
  }
}