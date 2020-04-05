/*
 *  Bleach.scala
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

import akka.stream.{Attributes, FanInShape4}
import de.sciss.fscape.stream.impl.deprecated.{FilterChunkImpl, FilterIn4DImpl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object Bleach {
  def apply(in: OutD, filterLen: OutI, feedback: OutD, filterClip: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(filterLen , stage.in1)
    b.connect(feedback  , stage.in2)
    b.connect(filterClip, stage.in3)
    stage.out
  }

  private final val name = "Bleach"

  private type Shp = FanInShape4[BufD, BufI, BufD, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.filterLen" ),
      in2 = InD (s"$name.feedback"  ),
      in3 = InD (s"$name.filterClip"),
      out = OutD(s"$name.out"       )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with FilterIn4DImpl [BufD, BufI, BufD, BufD]
      with FilterChunkImpl[BufD, BufD, Shp] {

    private[this] var feedback    = 0.0
    private[this] var filterClip  = 0.0
    private[this] var y1          = 0.0

    private[this] var kernel: Array[Double] = _
    private[this] var filterLen   = 0

    private[this] var winBuf: Array[Double] = _   // circular
    private[this] var winIdx      = 0

//    val START_FRAME = 0L
//    var STOP_FRAME  = START_FRAME + 16
//    var FRAMES_DONE = 0L

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0        = bufIn0.buf
      val b1        = if (bufIn1 == null) null else bufIn1.buf
      val stop1     = if (b1     == null) 0    else bufIn1.size
      val b2        = if (bufIn2 == null) null else bufIn2.buf
      val stop2     = if (b2     == null) 0    else bufIn2.size
      val b3        = if (bufIn3 == null) null else bufIn3.buf
      val stop3     = if (b3     == null) 0    else bufIn3.size
      val out       = bufOut0.buf

      var _feedback = feedback
      var _fltClip  = filterClip
      var _kernel   = kernel
      var _winBuf   = winBuf
      var _fltLen   = filterLen
      var _winIdx   = winIdx
      var _y1       = y1

      var inOffI    = inOff
      var outOffI   = outOff
      val stop0     = inOff + len
      while (inOffI < stop0) {
        if (inOffI < stop1) {
          _fltLen     = math.max(1, b1(inOffI))
          if (_fltLen != filterLen) {
            filterLen = _fltLen
            _kernel   = new Array[Double](_fltLen)
            kernel    = _kernel
            _winBuf   = new Array[Double](_fltLen)
            winBuf    = _winBuf
            _winIdx   = 0 // actually doesn't matter as new buffer is zero'ed
          }
        }
        if (inOffI < stop2) {
          _feedback = b2(inOffI)
//_feedback = 0.01
        }
        if (inOffI < stop3) {
          _fltClip = b3(inOffI)
        }

        // grab last input sample
        val x0    = b0(inOffI)

//        if (FRAMES_DONE >= START_FRAME && FRAMES_DONE < STOP_FRAME) {
//          println(s"---- frame $FRAMES_DONE")
//          val TMP_BUF = Vector.tabulate(8)(i => _winBuf((_winIdx + i) % _fltLen).toFloat)
//          println(s"buf ${TMP_BUF.mkString(", ")}")
//          println(s"flt ${_kernel.take(8).mkString(", ")}")
//        }

        // calculate output sample
        var i       = 0
        var j       = _winIdx
        var y0      = 0.0
        while (i < _fltLen) {
          y0     += _kernel(i) * _winBuf(j)
          i      += 1
          j       = (j + 1) % _fltLen
        }
        out(outOffI) = y0

        // update kernel
        i           = 0
        j           = _winIdx
        val errNeg  = x0 - y0
        val weight  = errNeg * _feedback
        while (i < _fltLen) {
          val f   = _kernel(i) + weight * _winBuf(j)
          _kernel(i) = math.max(-_fltClip, math.min(_fltClip, f))
          i      += 1
          j       = (j + 1) % _fltLen
        }

//        if (FRAMES_DONE >= START_FRAME && FRAMES_DONE < STOP_FRAME) {
//          println(s"in ${x0.toFloat}")
//          println(s"out ${y0.toFloat}")
//          println("w " + weight)
//          println(s"upd ${_kernel.take(8).mkString(", ")}")
//        }
//        FRAMES_DONE += 1

        // update window buffer (last element in the circular buffer)
        _winBuf(_winIdx) = x0
        _winIdx   = (_winIdx + 1) % _fltLen

        _y1       = y0
        inOffI   += 1
        outOffI  += 1
      }
      feedback    = _feedback
      filterClip  = _fltClip
      winIdx      = _winIdx
      y1          = _y1
    }
  }
}