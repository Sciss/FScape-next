/*
 *  PeakCentroid2D.scala
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

import akka.stream.Attributes
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.impl.{FilterLogicImpl, In6Out3Impl, In6Out3Shape, StageImpl, StageLogicImpl, WindowedLogicImpl}

object PeakCentroid2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh1: OutD, thresh2: OutD, radius: OutI)
           (implicit b: Builder): (OutD, OutD, OutD) = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in     , stage.in0)
    b.connect(width  , stage.in1)
    b.connect(height , stage.in2)
    b.connect(thresh1, stage.in3)
    b.connect(thresh2, stage.in4)
    b.connect(radius , stage.in5)
    (stage.out0, stage.out1, stage.out2)
  }

  private final val name = "PeakCentroid2D"

  private type Shape = In6Out3Shape[BufD, BufI, BufI, BufD, BufD, BufI, BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new In6Out3Shape(
      in0  = InD (s"$name.in"        ),
      in1  = InI (s"$name.width"     ),
      in2  = InI (s"$name.height"    ),
      in3  = InD (s"$name.thresh1"   ),
      in4  = InD (s"$name.thresh2"   ),
      in5  = InI (s"$name.radius"    ),
      out0 = OutD(s"$name.translateX"),
      out1 = OutD(s"$name.translateY"),
      out2 = OutD(s"$name.peak"      )
    )

    def createLogic(attr: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends StageLogicImpl(name, shape)
      with WindowedLogicImpl[BufD, Shape]
      with FilterLogicImpl  [BufD, Shape]
      with In6Out3Impl      [BufD, BufI, BufI, BufD, BufD, BufI, BufD, BufD, BufD] {

    override def toString = s"$name-L@${hashCode.toHexString}"

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()
    protected def allocOutBuf1(): BufD = ctrl.borrowBufD()
    protected def allocOutBuf2(): BufD = ctrl.borrowBufD()
    
    private[this] var width  : Int    = _
    private[this] var height : Int    = _
    private[this] var thresh1: Double = _
    private[this] var thresh2: Double = _
    private[this] var radius : Int    = _

    private[this] var winBuf      : Array[Double] = _
    private[this] var size        : Int = _

    /** Notifies about the start of the next window.
      *
      * @param inOff current offset into input buffer
      * @return the number of frames to write to the internal window buffer
      *         (becomes `writeToWinRemain`)
      */
    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = size
      if (bufIn1 != null && inOff < bufIn1.size) {
        width = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        height = math.max(2, bufIn2.buf(inOff))
      }
      size = width * height
      if (size != oldSize) {
        winBuf = new Array[Double](size)
      }

      if (bufIn3 != null && inOff < bufIn3.size) thresh1 = bufIn3.buf(inOff)
      if (bufIn4 != null && inOff < bufIn4.size) thresh2 = bufIn4.buf(inOff)
      if (bufIn5 != null && inOff < bufIn5.size) radius  = math.max(1, bufIn5.buf(inOff))

      size
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = {
      if (writeToWinOff < size) Util.clear(winBuf, writeToWinOff, size - writeToWinOff)

      
      ???
    }
  }
}