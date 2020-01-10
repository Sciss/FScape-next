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

import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.stream.impl.{FilterIn5DImpl, FilterLogicImpl, NodeImpl, StageImpl, WindowedLogicImpl}

import scala.annotation.tailrec
import scala.collection.mutable

/*

  XXX TODO: modulating window size and frac has not been tested

 */
object SlidingWindowPercentile {
  def apply(in: OutD, winSize: OutI, medianLen: OutI, frac: OutD, interp: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(winSize   , stage.in1)
    b.connect(medianLen , stage.in2)
    b.connect(frac      , stage.in3)
    b.connect(interp    , stage.in4)
    stage.out
  }

  private final val name = "SlidingWindowPercentile"

  private type Shape = FanInShape5[BufD, BufI, BufI, BufD, BufI, BufD]

//  private final val lessThanOne = java.lang.Double.longBitsToDouble(0x3fefffffffffffffL)
  private final val lessThanOne = java.lang.Math.nextDown(1.0)

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape5(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.winSize"   ),
      in2 = InI (s"$name.medianLen" ),
      in3 = InD (s"$name.frac"      ),
      in4 = InI (s"$name.interp"    ),
      out = OutD(s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Pixel {
    var medianBuf    : Array[Double] = _
    var medianBufIdx  = 0

    // we follow the typical approach with two priority queues,
    // split at the percentile
    val pqLo  = new mutable.PriorityQueueWithRemove[Double]
    val pqHi  = new mutable.PriorityQueueWithRemove[Double]
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterLogicImpl[BufD, Shape]
      with WindowedLogicImpl[Shape]
      with FilterIn5DImpl[BufD, BufI, BufI, BufD, BufI] {

    private[this] var winSize     : Int     = 0
    private[this] var medianLen   : Int     = 0
    private[this] var frac        : Double  = -1d
    private[this] var interp      : Boolean = _

    private[this] var pixels      : Array[Pixel]  = _
    private[this] var winBuf      : Array[Double] = _

    override protected def stopped(): Unit = {
      super.stopped()
      pixels = null
    }

    protected def startNextWindow(inOff: Int): Long = {
      var _needsUpdate = false
      if (bufIn1 != null && inOff < bufIn1.size) {
        val newWinSize = math.max(1, bufIn1.buf(inOff))
        if (winSize != newWinSize) {
          winSize     = newWinSize
          val _pixels = new Array[Pixel](newWinSize)
          var i = 0
          while (i < _pixels.length) {
            _pixels(i) = new Pixel
            i += 1
          }
          pixels        = _pixels
          winBuf        = new Array[Double](newWinSize)
          _needsUpdate  = true
        }
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        val newMedLen = math.max(1, bufIn2.buf(inOff))
        if (medianLen != newMedLen) {
          medianLen = newMedLen
          _needsUpdate = true
        }
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        // such that `(frac * n).toInt < n` holds
        val newFrac = math.max(0d, math.min(lessThanOne, bufIn3.buf(inOff)))
        if (frac != newFrac) {
          frac = newFrac
          _needsUpdate = true
        }
      }
      if (bufIn4 != null && inOff < bufIn4.size) {
        interp = bufIn4.buf(inOff) != 0
      }

      if (_needsUpdate) {
        val _pixels = pixels
        var i = 0
        while (i < _pixels.length) {
          updatePixel(_pixels(i))
          i += 1
        }
      }

      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
      val _pixels = pixels
      var i         = 0
      val stop    = writeToWinOff.toInt

      while (i < stop) {
        val valueIn   = winBuf(i)
        val valueOut  = processPixel(valueIn, _pixels(i))
        winBuf(i)     = valueOut
        i += 1
      }

      writeToWinOff
    }

    private def updatePixel(pixel: Pixel): Unit = {
      import pixel._

      @inline
      def calcTotSize(): Int = pqLo.size + pqHi.size

      val oldSize = calcTotSize()
      var shrink  = oldSize - medianLen
      val newSize = shrink != 0
      if (newSize) {
        val newBuf    = new Array[Double](medianLen)
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

    private def removePQ(pixel: Pixel, d: Double): Unit = {
      import pixel._
      val pqRem = if (pqLo.nonEmpty && pqLo.max >= d) pqLo else pqHi
      assert(pqRem.remove(d))
    }

    @inline
    private def calcTarget(szTot: Int): Int = {
      val idxTgtD = frac * szTot
      idxTgtD.toInt
    }

    // tot-size must be > 0
    @tailrec
    private def balancePQ(pixel: Pixel, tgt: Int): Unit = {
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

    private def processPixel(valueIn: Double, pixel: Pixel): Double = {
      import pixel._

      @inline
      def calcTotSize(): Int = pqLo.size + pqHi.size

      val pqIns    = if (pqLo.isEmpty || valueIn < pqLo.max) pqLo else pqHi
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