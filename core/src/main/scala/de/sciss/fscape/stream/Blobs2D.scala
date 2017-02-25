/*
 *  Blobs2D.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.stream.impl.{FilterIn4DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

object Blobs2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(width , stage.in1)
    b.connect(height, stage.in2)
    b.connect(thresh, stage.in3)
    stage.out
  }

  private final val name = "Blobs2D"

  private type Shape = FanInShape4[BufD, BufI, BufI, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.width" ),
      in2 = InI (s"$name.height"),
      in3 = InD (s"$name.thresh"),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn4DImpl[BufD, BufI, BufI, BufD] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var dotBuf    : Array[Double] = _
    private[this] var width     : Int           = _
    private[this] var height    : Int           = _
    private[this] var winSize   : Int           = _
    private[this] var normalize : Boolean       = _

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        width = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        height = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        normalize = bufIn3.buf(inOff) > 0
      }
      winSize = width * height
      if (winSize != oldSize) {
        winBuf = new Array(winSize)
        dotBuf = new Array(width   )
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
      dotBuf = null
    }

    // cf. https://en.wikipedia.org/wiki/Gram%E2%80%93Schmidt_process#Numerical_stability
    // cf. https://gist.github.com/Sciss/e9ed09f4e1e06b4fe379b16378fb5bb5
    protected def processWindow(writeToWinOff: Long): Long = {
      val a     = winBuf
      val size  = winSize
      if (writeToWinOff < size) {
        val writeOffI = writeToWinOff.toInt
        Util.clear(a, writeOffI, size - writeOffI)
      }

      ???

      size
    }
  }
}