/*
 *  DCT_II.scala
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
import de.sciss.fscape.stream.impl.Handlers.InIAux
import de.sciss.fscape.stream.impl.StageImpl
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA

import scala.math.max

object DCT_II {
  def apply(in: OutD, size: OutI, numCoeffs: OutI, zero: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(size      , stage.in1)
    b.connect(numCoeffs , stage.in2)
    b.connect(zero      , stage.in3)
    stage.out
  }

  private final val name = "DCT_II"

  private type Shp = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.size"     ),
      in2 = InI (s"$name.numCoeffs"),
      in3 = InI (s"$name.zero"     ),
      out = OutD(s"$name.out"      )
    )
    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  // XXX TODO --- we could store pre-calculated cosine tables for
  // sufficiently small table sizes
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends FilterWindowedInAOutA[Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hSize       = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hNumCoeffs  = InIAux  (this, shape.in2)(max(1, _))
    private[this] val hZero       = InIAux  (this, shape.in3)()

    private[this] var size      = 0
    private[this] var zero      = false
    private[this] var numCoeffs = -1

    private[this] var coefBuf : Array[Double] = _

    override protected def stopped(): Unit = {
      super.stopped()
      coefBuf = null
    }

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hNumCoeffs.hasNext && hZero.hasNext
      if (ok) {
        size = hSize.next()
        zero = hZero.next() > 0
        val numCoeffsOld = numCoeffs
        numCoeffs = hNumCoeffs.next()
        if (numCoeffs != numCoeffsOld) {
          coefBuf = new Array[Double](numCoeffs)
        }
      }
      ok
    }

    protected def winBufSize: Int = size

    override protected def writeWinSize: Long = numCoeffs

    override protected def writeFromWindow(n: Layer): Unit = {
      val offI = writeOff.toInt
      hOut.nextN(coefBuf, offI, n)
    }

    protected def processWindow(): Unit = {
      val _size       = size
      val _numCoeffs  = numCoeffs
      val _bufIn      = winBuf
      val _bufOut     = coefBuf

      Util.clear(_bufOut, 0, _numCoeffs)
      val r     = math.Pi / _size   // XXX TODO --- divide by `_size` or `_numCoeffs`?
      val nP    = if (zero) 0 else 1
      var n     = 0
      while (n < _numCoeffs) {
        val s = r * (n + nP)
        var i = 0
        while (i < _size) {
          _bufOut(n) += _bufIn(i) * math.cos(s * (i + 0.5))
          i += 1
        }
        n += 1
      }
    }
  }
}