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
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn4DImpl, NodeImpl, StageImpl}

import scala.collection.mutable

/*

  TODO --- check out this: http://arxiv.org/abs/cs/0610046

  (I haven't read it, but obviously if the window is sorted,
  we can drop, insert or query an element in O(log N)).

 */
object SlidingPercentile {
  def apply(in: OutD, size: OutI, frac: OutD, interp: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(size  , stage.in1)
    b.connect(frac  , stage.in2)
    b.connect(interp, stage.in3)
    stage.out
  }

  private final val name = "SlidingPercentile"

  private type Shape = FanInShape4[BufD, BufI, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.size"  ),
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
      with ChunkImpl[Shape]
      with FilterIn4DImpl[BufD, BufI, BufD, BufI] {
    
    private[this] var size  : Int     = 0
    private[this] var frac  : Double  = -1d
    private[this] var interp: Boolean = _

    // we follow the typical approach with two priority queues,
    // split at the percentile
    private[this] val pqLo  = new mutable.PriorityQueueWithRemove[Double]
    private[this] val pqHi  = new mutable.PriorityQueueWithRemove[Double]

    protected def shouldComplete(): Boolean =
      inRemain == 0 && isClosed(in0) && !isAvailable(in0)

    protected def processChunk(): Boolean = {
      val chunk = math.min(inRemain, outRemain)
      val res   = chunk > 0
      if (res) {
        processChunk(inOff = inOff, outOff = outOff, chunk = chunk)
        inOff       += chunk
        inRemain    -= chunk
        outOff      += chunk
        outRemain   -= chunk
      }
      res
    }

    private def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
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
      var _size   = size
      var _frac   = frac
      var _interp = interp
      val _pqLo   = pqLo
      val _pqHi   = pqHi

      while (inOffI < stop0) {
        val valueIn = b0(inOffI)
        var needsUpdate = false
        if (inOffI < stop1) {
          val newSize = math.max(1, b1(inOffI))
          if (_size != newSize) {
            _size = newSize
            needsUpdate = true
          }
        }
        if (inOffI < stop2) {
          val newFrac = math.max(0d, math.min(1d, b2(inOffI)))
          if (_frac != newFrac) {
            _frac = newFrac
            needsUpdate = true
          }
        }
        if (inOffI < stop3) {
          _interp = b3(inOffI) != 0
        }

        if (needsUpdate) {
          println("SlidingPercentile - needsUpdate - TODO")
        }

        val pqIns     = if (_pqLo.isEmpty || valueIn < _pqLo.max) _pqLo else _pqHi
        pqIns.add(valueIn)
        val szTot     = _pqLo.size + _pqHi.size
//        val idxTgt    = (_frac * szTot + 0.5).toInt
        val idxTgtD   =_frac * szTot
        val idxTgt    = {
          val tmp = idxTgtD.toInt
          if (tmp == szTot) tmp - 1 else tmp    // this can happen for frac == 1
        }
        val idxInDif  = _pqLo.size - idxTgt
        if (idxInDif <= 0) {
          _pqLo.add(_pqHi.removeMin())
        } else if (idxInDif > 1) {
          _pqHi.add(_pqLo.removeMax())
        }
        val idxOutDif = _pqLo.size - idxTgt

        val valueOut = if (_interp) {
          val idxTgtM = idxTgtD % 1.0
          ???
        } else {
          if      (idxOutDif == 1) _pqLo.max
          else if (idxOutDif == 0) {
            println("WOW")
            _pqHi.min
          }
          else ???
        }

        out(outOffI) = valueOut
        inOffI  += 1
        outOffI += 1
      }
      size    = _size
      frac    = _frac
      interp  = _interp
    }
  }
}