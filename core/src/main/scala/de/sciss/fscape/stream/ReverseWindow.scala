/*
 *  ReverseWindow.scala
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
import de.sciss.fscape.stream.impl.{DemandFilterWindowedLogic, NodeImpl, StageImpl}

/** Reverses contents of windowed input. */
object ReverseWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param clump  clump size within each window. With a clump size of one,
    *               each window is reversed sample by sample, if the clump size
    *               is two, the first two samples are flipped with the last
    *               two samples, then the third and forth are flipped with the
    *               third and forth before last, etc. Like `size`, `clump` is
    *               sampled at each beginning of a new window and held constant
    *               during the window.
    */
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, clump: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(clump , stage.in2)
    stage.out
  }

  private final val name = "ReverseWindow"

  private type Shp[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InI       (s"$name.clump"),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isInt)
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)
      else if (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)
      else if (tpe.isDouble)
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)
      else
        new Logic[A, E](shape, layer)

      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Logic[@specialized(Int, Long, Double) A,
    E <: BufElem[A]](shape: Shp[E], layer: Layer)(implicit ctrl: Control, protected val tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with DemandFilterWindowedLogic[A, E, Shp[E]] {

    private[this] var clump       : Int     = -1
    private[this] var bufClumpOff : Int     = 0
    private[this] var bufClump    : BufI    = _
    private[this] var needsClump  : Boolean = true

    // constructor
    {
      installMainAndWindowHandlers()
      new _InHandlerImpl(inletClump)(clumpValid)
    }

    private def clumpValid = clump >= 0

    protected def inletSignal : Inlet[E]  = shape.in0
    protected def inletWinSize: InI       = shape.in1
    protected def inletClump  : InI       = shape.in2

    protected def out0        : Outlet[E] = shape.out

    protected def winParamsValid: Boolean = clumpValid
    protected def needsWinParams: Boolean = needsClump

    protected def requestWinParams(): Unit = {
      needsClump = true
    }

    protected def freeWinParamBuffers(): Unit =
      freeClumpBuf()

    private def freeClumpBuf(): Unit =
      if (bufClump != null) {
        bufClump.release()
        bufClump = null
      }

    protected def tryObtainWinParams(): Boolean =
      if (needsClump && bufClump != null && bufClumpOff < bufClump.size) {
        clump       = math.max(1, bufClump.buf(bufClumpOff))
        bufClumpOff += 1
        needsClump = false
        true
      } else if (isAvailable(inletClump)) {
        freeClumpBuf()
        bufClump    = grab(inletClump)
        bufClumpOff = 0
        tryPull(inletClump)
        true
      } else if (needsClump && isClosed(inletClump) && clumpValid) {
        needsClump = false
        true
      } else {
        false
      }

    override protected def prepareWindow(win: Array[A], winInSize: Int, inSignalDone: Boolean): Long = {
      var i   = 0
      val cl  = clump
      val cl2 = cl + cl
      var j   = winInSize - cl
      while (i < j) {
        val k = i + cl
        while (i < k) {
          val tmp = win(i)
          win(i) = win(j)
          win(j) = tmp
          i += 1
          j += 1
        }
        j -= cl2
      }
      winInSize
    }
  }
}