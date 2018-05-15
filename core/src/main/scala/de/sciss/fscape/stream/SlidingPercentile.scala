/*
 *  SlidingPercentile.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.impl.{FilterIn4DImpl, NodeImpl, SameChunkImpl, StageImpl}

import scala.annotation.tailrec
import scala.collection.mutable

/*

  XXX TODO: modulating window size and frac has not been tested

 */
object SlidingPercentile {
  def apply(in: OutD, len: OutI, frac: OutD, interp: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(len   , stage.in1)
    b.connect(frac  , stage.in2)
    b.connect(interp, stage.in3)
    stage.out
  }

  private final val name = "SlidingPercentile"

  private type Shape = FanInShape4[BufD, BufI, BufD, BufI, BufD]

  private final val lessThanOne = java.lang.Double.longBitsToDouble(0x3fefffffffffffffL)

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.len"   ),
      in2 = InD (s"$name.frac"  ),
      in3 = InI (s"$name.interp"),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

//  private object InvertedDoubleOrdering extends DoubleOrdering {
//    override def compare(x: Double, y: Double): Int = java.lang.Double.compare(y, x)
//  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with SameChunkImpl[Shape]
      with FilterIn4DImpl[BufD, BufI, BufD, BufI] {
    
    private[this] var medianLen   : Int     = 0
    private[this] var frac        : Double  = -1d
    private[this] var interp      : Boolean = _

    private[this] var medianBuf   : Array[Double] = _
    private[this] var medianBufIdx: Int     = 0

    // we follow the typical approach with two priority queues,
    // split at the percentile
    private[this] val pqLo  = new mutable.PriorityQueueWithRemove[Double]
    private[this] val pqHi  = new mutable.PriorityQueueWithRemove[Double]

    protected def shouldComplete(): Boolean =
      inRemain == 0 && isClosed(in0) && !isAvailable(in0)

    override protected def stopped(): Unit = {
      super.stopped()
      medianBuf = null
    }

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOffI + chunk
      val b0      = bufIn0 .buf
      val out     = bufOut0.buf
      val b1      = if (bufIn1  == null) null else bufIn1.buf
      val stop1   = if (b1      == null) 0    else bufIn1.size
      val b2      = if (bufIn2  == null) null else bufIn2.buf
      val stop2   = if (b2      == null) 0    else bufIn2.size
      val b3      = if (bufIn3  == null) null else bufIn3.buf
      val stop3   = if (b3      == null) 0    else bufIn3.size
      var _medLen = medianLen
      var _frac   = frac
      var _interp = interp
      val _pqLo   = pqLo
      val _pqHi   = pqHi
      var _medBuf = medianBuf
      var _medIdx = medianBufIdx

      while (inOffI < stop0) {
        @inline
        def calcTotSize(): Int = _pqLo.size + _pqHi.size

        @inline
        def calcTarget(szTot: Int): Int = {
          val idxTgtD   =_frac * szTot
          idxTgtD.toInt
        }

        // tot-size must be > 0
        @tailrec
        def balance(tgt: Int): Unit = {
          val idxInDif = _pqLo.size - tgt
          if (idxInDif <= 0) {
            _pqLo.add(_pqHi.removeMin())
            if (idxInDif < 0) balance(tgt)
          } else if (idxInDif >= 2) {
            _pqHi.add(_pqLo.removeMax())
            if (idxInDif > 2) balance(tgt)
          }
        }

        def remove(d: Double): Unit = {
          val pqRem = if (_pqLo.nonEmpty && _pqLo.max >= d) _pqLo else _pqHi
          assert(pqRem.remove(d))
        }

        val valueIn = b0(inOffI)
        var needsUpdate = false
        if (inOffI < stop1) {
          val newMedLen = math.max(1, b1(inOffI))
          if (_medLen != newMedLen) {
            _medLen = newMedLen
            needsUpdate = true
          }
        }
        if (inOffI < stop2) {
          // such that `(frac * n).toInt < n` holds
          val newFrac = math.max(0d, math.min(lessThanOne, b2(inOffI)))
          if (_frac != newFrac) {
            _frac = newFrac
            needsUpdate = true
          }
        }
        if (inOffI < stop3) {
          _interp = b3(inOffI) != 0
        }

        if (needsUpdate) {
          val oldSize = calcTotSize()
          var shrink  = oldSize - _medLen
          val newSize = shrink != 0
          if (newSize) {
            val newBuf    = new Array[Double](_medLen)
            val oldMedIdx = _medIdx
            val chunk     = math.min(_medLen, oldSize)
            if (chunk > 0) {  // since _size must be > 0, it implies that oldSize > 0
              val oldBuf    = _medBuf
              val off1      = (oldMedIdx - chunk + oldBuf.length) % oldBuf.length // begin of most recent entries
              val num1      = math.min(chunk, oldBuf.length - off1)
              System.arraycopy(oldBuf, off1, newBuf, 0, num1)
              if (num1 < chunk) {
                System.arraycopy(oldBuf, 0, newBuf, num1, chunk - num1)
              }

              var off2 = (oldMedIdx - oldSize + oldBuf.length) % oldBuf.length // begin of oldest entries
              while (shrink > 0) {
                val valueRem = oldBuf(off2)
                remove(valueRem)
                shrink -= 1
                off2   += 1
                if (off2 == oldBuf.length) off2 = 0
              }
            }
            _medIdx = chunk % newBuf.length
            _medBuf = newBuf
          }
          val tmpTot = calcTotSize()
          if (tmpTot > 0) {
            val tmpTgt  = calcTarget(tmpTot)
            balance(tmpTgt)
          }
        }

        val pqIns         = if (_pqLo.isEmpty || valueIn < _pqLo.max) _pqLo else _pqHi
        val valueOld      = _medBuf(_medIdx)
        _medBuf(_medIdx)  = valueIn
        _medIdx += 1
        if (_medIdx == _medBuf.length) _medIdx = 0

        pqIns.add(valueIn)
        val szTot = {
          val tmp = calcTotSize()
          if (tmp > _medLen) {
            remove(valueOld)
            tmp - 1
          } else {
            tmp
          }
        }
        val idxTgt = calcTarget(szTot)
        balance(idxTgt)

        val idxOutDif = _pqLo.size - idxTgt

        val valueOut = if (_interp) {
          // val idxTgtM = idxTgtD % 1.0
          ???
        } else {
          assert (idxOutDif == 1)
          _pqLo.max

//          if      (idxOutDif == 1) _pqLo.max
//          else if (idxOutDif == 0) {
//            println("SlidingPercentile - idxOutDif == 0, oops, assertion failed")
//            _pqHi.min
//          }
//          else ...
        }

        out(outOffI) = valueOut
        inOffI  += 1
        outOffI += 1
      }
      medianLen     = _medLen
      frac          = _frac
      interp        = _interp
      medianBuf     = _medBuf
      medianBufIdx  = _medIdx
    }
  }
}