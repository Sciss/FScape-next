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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, Inlet}
import de.sciss.fscape.stream.impl.{FilterLogicImpl, In6Out3Impl, In6Out3Shape, StageImpl, StageLogicImpl, WindowedLogicImpl}

object PeakCentroid2D {
  def apply(in: OutD, width: OutI, height: OutI, thresh1: OutD, thresh2: OutD, radius: OutI): (OutD, OutD, OutD) = {
    ???
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

    protected def in0: Inlet[BufD] = shape.in0

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()
    protected def allocOutBuf1(): BufD = ctrl.borrowBufD()
    protected def allocOutBuf2(): BufD = ctrl.borrowBufD()

    /** Notifies about the start of the next window.
      *
      * @param inOff current offset into input buffer
      * @return the number of frames to write to the internal window buffer
      *         (becomes `writeToWinRemain`)
      */
    protected def startNextWindow(inOff: Int): Int = ???

    /** Issues a copy from input buffer to internal window buffer.
      *
      * @param inOff         current offset into input buffer
      * @param writeToWinOff current offset into internal window buffer
      * @param chunk         number of frames to copy
      */
    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit = ???

    /** Called when the internal window buffer is full, in order to
      * proceed to the next phase of copying from window to output.
      * (transitioning between `copyInputToWindow` and `copyWindowToOutput`)
      *
      * @param writeToWinOff the current offset into the internal window buffer.
      *                      this is basically the amount of frames available for
      *                      processing.
      * @param flush         `true` if the input is exhausted.
      * @return the number of frames available for sending through `copyWindowToOutput`
      *         (this becomes `readFromWinRemain`).
      */
    protected def processWindow(writeToWinOff: Int, flush: Boolean): Int = ???

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit = ???
  }
}