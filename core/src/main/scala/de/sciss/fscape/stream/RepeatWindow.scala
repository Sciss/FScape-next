/*
 *  RepeatWindowNew.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.deprecated.DemandFilterWindowedLogic
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

/** Repeats contents of windowed input.
  */
object RepeatWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param num    the number of times each window is repeated
    */
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, num: OutL)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(num , stage.in2)
    stage.out
  }

  private final val name = "RepeatWindow"

  private type Shp[E] = FanInShape3[E, BufI, BufL, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InL       (s"$name.num"  ),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, protected val tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
    with DemandFilterWindowedLogic[A, E, Shp[E]] {

    private[this] var num         : Long  = -1
    private[this] var bufNumOff   : Int   = 0
    private[this] var bufNum      : BufL  = _
    private[this] var needsNum = true

    private def numValid = num >= 0L

    // constructor
    {
      installMainAndWindowHandlers()
      new _InHandlerImpl(inletNum)(numValid)
    }

    protected def inletSignal : Inlet[E]  = shape.in0
    protected def inletWinSize: InI       = shape.in1
    private   def inletNum    : InL       = shape.in2

    protected def out0        : Outlet[E] = shape.out

    protected def winParamsValid: Boolean = numValid
    protected def needsWinParams: Boolean = needsNum

    protected def requestWinParams(): Unit = {
      needsNum = true
    }

    protected def freeWinParamBuffers(): Unit =
      freeNumBuf()

    private def freeNumBuf(): Unit =
      if (bufNum != null) {
        bufNum.release()
        bufNum = null
      }

    protected def tryObtainWinParams(): Boolean =
      if (needsNum && bufNum != null && bufNumOff < bufNum.size) {
        num       = math.max(0 /*1*/, bufNum.buf(bufNumOff))
        bufNumOff   += 1
        needsNum = false
        true
      } else if (isAvailable(inletNum)) {
        freeNumBuf()
        bufNum    = grab(inletNum)
        bufNumOff = 0
        tryPull(inletNum)
        true
      } else if (needsNum && isClosed(inletNum) && numValid) {
        needsNum = false
        true
      } else {
        false
      }

    override protected def prepareWindow(in: Array[A], winSize: Int, inSignalDone: Boolean): Long =
      num * winSize

    override protected def processOutput(in : Array[A], winInSize : Int , writeOff : Long,
                                         out: Array[A], winOutSize: Long, outOff   : Int , chunk: Int): Unit = {
      var rem       = chunk
      var outOff0   = outOff
      var writeOff0 = writeOff
      while (rem > 0) {
        val i     = (writeOff0 % winInSize).toInt
        val j     = math.min(rem, winInSize - i)
        System.arraycopy(in, i, out, outOff0, j)
        outOff0   += j
        writeOff0 += j
        rem       -= j
      }
    }
  }
}