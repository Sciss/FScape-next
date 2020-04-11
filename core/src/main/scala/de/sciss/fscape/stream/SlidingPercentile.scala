/*
 *  SlidingPercentile.scala
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

import akka.stream.{Attributes, FanInShape4, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InIAux}
import de.sciss.fscape.stream.impl.logic.FilterInAOutB
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.{max, min}

object SlidingPercentile {
  def apply[A, E <: BufElem[A]](in: Outlet[E], len: OutI, frac: OutD, interp: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(len   , stage.in1)
    b.connect(frac  , stage.in2)
    b.connect(interp, stage.in3)
    stage.out
  }

  private final val name = "SlidingPercentile"

  private type Shp[E] = FanInShape4[E, BufI, BufD, BufI, E]

//  private final val lessThanOne = java.lang.Double.longBitsToDouble(0x3fefffffffffffffL)
  private final val lessThanOne = java.lang.Math.nextDown(1.0)

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape4(
      in0 = Inlet [E] (s"$name.in"    ),
      in1 = InI       (s"$name.len"   ),
      in2 = InD       (s"$name.frac"  ),
      in3 = InI       (s"$name.interp"),
      out = Outlet[E] (s"$name.out"   )
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

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)(lt: (A, A) => Boolean)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends FilterInAOutB[A, E, A, E, Shp[E]](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hMedianLen  = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hFrac       = InDAux  (this, shape.in2)(_.clip(0.0, lessThanOne))
    private[this] val hInterp     = InIAux  (this, shape.in3)()

    private[this] var medianLen   : Int     = 0
    private[this] var frac        : Double  = -1d

    private[this] var medianBuf   : Array[A] = _
    private[this] var medianBufIdx: Int     = 0

    import tpe.ordering

    // we follow the typical approach with two priority queues,
    // split at the percentile
    private[this] val pqLo  = new mutable.PriorityQueueWithRemove[A]
    private[this] val pqHi  = new mutable.PriorityQueueWithRemove[A]

    override protected def stopped(): Unit = {
      super.stopped()
      medianBuf = null
    }

    protected def auxAvailable: Int =
      min(hMedianLen.available, min(hFrac.available, hInterp.available))

    protected def run(in: Array[A], inOff: Int, out: Array[A], outOff: Int, chunk: Int): Unit = {
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOffI + chunk
      var _medLen = medianLen
      var _frac   = frac
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

        def remove(d: A): Unit = {
          val pqRem = if (_pqLo.nonEmpty && !lt(_pqLo.max, d)) _pqLo else _pqHi
          assert(pqRem.remove(d))
        }

        val valueIn = in(inOffI)
        var needsUpdate = false

        val newMedLen = hMedianLen.next()
        if (_medLen != newMedLen) {
          _medLen = newMedLen
          needsUpdate = true
        }

        // such that `(frac * n).toInt < n` holds
        val newFrac = hFrac.next()
        if (_frac != newFrac) {
          _frac = newFrac
          needsUpdate = true
        }

        val _interp = hInterp.next() > 0

        if (needsUpdate) {
          val oldSize = calcTotSize()
          var shrink  = oldSize - _medLen
          val newSize = shrink != 0
          if (newSize) {
            val newBuf    = tpe.newArray(_medLen)
            val oldMedIdx = _medIdx
            val chunk     = min(_medLen, oldSize)
            if (chunk > 0) {  // since _size must be > 0, it implies that oldSize > 0
              val oldBuf    = _medBuf
              val off1      = (oldMedIdx - chunk + oldBuf.length) % oldBuf.length // begin of most recent entries
              val num1      = min(chunk, oldBuf.length - off1)
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

        val pqIns         = if (_pqLo.isEmpty || lt(valueIn, _pqLo.max)) _pqLo else _pqHi
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
      medianBuf     = _medBuf
      medianBufIdx  = _medIdx
    }
  }
}