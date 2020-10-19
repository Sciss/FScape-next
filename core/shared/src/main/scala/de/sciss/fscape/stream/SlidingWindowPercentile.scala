/*
 *  SlidingWindowPercentile.scala
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

import akka.stream.{Attributes, FanInShape5, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InIAux}
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.max

object SlidingWindowPercentile {
  def apply[A, E <: BufElem[A]](in: Outlet[E], winSize: OutI, medianLen: OutI, frac: OutD, interp: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(winSize   , stage.in1)
    b.connect(medianLen , stage.in2)
    b.connect(frac      , stage.in3)
    b.connect(interp    , stage.in4)
    stage.out
  }

  private final val name = "SlidingWindowPercentile"

  private type Shp[E] = FanInShape5[E, BufI, BufI, BufD, BufI, E]

//  private final val lessThanOne = java.lang.Double.longBitsToDouble(0x3fefffffffffffffL)
  private final val lessThanOne = java.lang.Math.nextDown(1.0)

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape5(
      in0 = Inlet [E] (s"$name.in"        ),
      in1 = InI       (s"$name.winSize"   ),
      in2 = InI       (s"$name.medianLen" ),
      in3 = InD       (s"$name.frac"      ),
      in4 = InI       (s"$name.interp"    ),
      out = Outlet[E] (s"$name.out"       )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new Logic[Double, BufD](shape.asInstanceOf[Shp[BufD]], layer)(_ < _)
      } else if (tpe.isInt) {
        new Logic[Int   , BufI](shape.asInstanceOf[Shp[BufI]], layer)(_ < _)
      } else {
        assert (tpe.isLong)
        new Logic[Long  , BufL](shape.asInstanceOf[Shp[BufL]], layer)(_ < _)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private final class Pixel[A: Ordering] {
    var medianBuf    : Array[A] = _
    var medianBufIdx  = 0

    // we follow the typical approach with two priority queues,
    // split at the percentile
    val pqLo  = new mutable.PriorityQueueWithRemove[A]
    val pqHi  = new mutable.PriorityQueueWithRemove[A]
  }

  private final class Logic[@specialized(Args) A, E <: BufElem[A]](shape: Shp[E], layer: Layer)(lt: (A, A) => Boolean)
                                                                  (implicit ctrl: Control, tpe0: StreamType[A, E])
    extends FilterWindowedInAOutA[A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hWinSize    = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hMedianLen  = InIAux  (this, shape.in2)(max(1, _))
    private[this] val hFrac       = InDAux  (this, shape.in3)(_.clip(0.0, lessThanOne))
    private[this] val hInterp     = InIAux  (this, shape.in4)()

    private[this] var winSize     : Int     = 0
    private[this] var medianLen   : Int     = 0
    private[this] var frac        : Double  = -1d
    private[this] var interp      : Boolean = _

    private[this] var pixels      : Array[Pixel[A]]  = _

    override protected def stopped(): Unit = {
      super.stopped()
      pixels = null
    }

    protected def winBufSize: Int = winSize

    import tpe.ordering

    protected def tryObtainWinParams(): Boolean = {
      val ok =
        hWinSize  .hasNext &&
        hMedianLen.hasNext &&
        hFrac     .hasNext &&
        hInterp   .hasNext

      if (ok) {
        var _needsUpdate = false
        val newWinSize = hWinSize.next()
        if (winSize != newWinSize) {
          winSize     = newWinSize
          val _pixels = new Array[Pixel[A]](newWinSize)
          var i = 0
          while (i < _pixels.length) {
            _pixels(i) = new Pixel[A]
            i += 1
          }
          pixels        = _pixels
          _needsUpdate  = true
        }
        val newMedLen = hMedianLen.next()
        if (medianLen != newMedLen) {
          medianLen = newMedLen
          _needsUpdate = true
        }
        // such that `(frac * n).toInt < n` holds
        val newFrac = hFrac.next()
        if (frac != newFrac) {
          frac = newFrac
          _needsUpdate = true
        }

        interp = hInterp.next() > 0

        if (_needsUpdate) {
          val _pixels = pixels
          var i = 0
          while (i < _pixels.length) {
            updatePixel(_pixels(i))
            i += 1
          }
        }
      }
      ok
    }

    protected def processWindow(): Unit = {
      val _pixels = pixels
      var i         = 0
      val stop    = readOff.toInt

      while (i < stop) {
        val valueIn   = winBuf(i)
        val valueOut  = processPixel(valueIn, _pixels(i))
        winBuf(i)     = valueOut
        i += 1
      }
    }

    private def updatePixel(pixel: Pixel[A]): Unit = {
      import pixel._

      @inline
      def calcTotSize(): Int = pqLo.size + pqHi.size

      val oldSize = calcTotSize()
      var shrink  = oldSize - medianLen
      val newSize = shrink != 0
      if (newSize) {
        val newBuf    = tpe.newArray(medianLen)
        val oldMedIdx = medianBufIdx
        val chunk     = math.min(medianLen, oldSize)
        if (chunk > 0) {  // since _size must be > 0, it implies that oldSize > 0
          val oldBuf    = medianBuf
          val off1      = (oldMedIdx - chunk + oldBuf.length) % oldBuf.length // begin of most recent entries
          val num1      = math.min(chunk, oldBuf.length - off1)
          System.arraycopy(oldBuf, off1, newBuf, 0, num1)
          if (num1 < chunk) {
            System.arraycopy(oldBuf, 0, newBuf, num1, chunk - num1)
          }

          var off2 = (oldMedIdx - oldSize + oldBuf.length) % oldBuf.length // begin of oldest entries
          while (shrink > 0) {
            val valueRem = oldBuf(off2)
            removePQ(pixel, valueRem)
            shrink -= 1
            off2   += 1
            if (off2 == oldBuf.length) off2 = 0
          }
        }
        medianBufIdx  = chunk % newBuf.length
        medianBuf     = newBuf
      }
      val tmpTot = calcTotSize()
      if (tmpTot > 0) {
        val tmpTgt  = calcTarget(tmpTot)
        balancePQ(pixel, tmpTgt)
      }
    }

    private def removePQ(pixel: Pixel[A], d: A): Unit = {
      import pixel._
      val pqRem = if (pqLo.nonEmpty && !lt(pqLo.max, d)) pqLo else pqHi
      assert(pqRem.remove(d))
    }

    @inline
    private def calcTarget(szTot: Int): Int = {
      val idxTgtD = frac * szTot
      idxTgtD.toInt
    }

    // tot-size must be > 0
    @tailrec
    private def balancePQ(pixel: Pixel[A], tgt: Int): Unit = {
      import pixel._
      val idxInDif = pqLo.size - tgt
      if (idxInDif <= 0) {
        pqLo.add(pqHi.removeMin())
        if (idxInDif < 0) balancePQ(pixel, tgt)
      } else if (idxInDif >= 2) {
        pqHi.add(pqLo.removeMax())
        if (idxInDif > 2) balancePQ(pixel, tgt)
      }
    }

    private def processPixel(valueIn: A, pixel: Pixel[A]): A = {
      import pixel._

      @inline
      def calcTotSize(): Int = pqLo.size + pqHi.size

      val pqIns    = if (pqLo.isEmpty || lt(valueIn, pqLo.max)) pqLo else pqHi
      val valueOld = medianBuf(medianBufIdx)
      medianBuf(medianBufIdx) = valueIn
      medianBufIdx += 1
      if (medianBufIdx == medianBuf.length) medianBufIdx = 0

      pqIns.add(valueIn)
      val szTot = {
        val tmp = calcTotSize()
        if (tmp > medianLen) {
          removePQ(pixel, valueOld)
          tmp - 1
        } else {
          tmp
        }
      }
      val idxTgt = calcTarget(szTot)
      balancePQ(pixel, idxTgt)

      val idxOutDif = pqLo.size - idxTgt

      val valueOut = if (interp) {
        // val idxTgtM = idxTgtD % 1.0
        ???
      } else {
        assert (idxOutDif == 1)
        pqLo.max
      }

      valueOut
    }
  }
}