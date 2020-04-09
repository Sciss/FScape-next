/*
 *  GramSchmidtMatrix.scala
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
import de.sciss.fscape.stream.impl.logic.FilterWindowedInAOutA
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

import scala.math.max

object GramSchmidtMatrix {
  def apply(in: OutD, rows: OutI, columns: OutI, normalize: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in       , stage.in0)
    b.connect(rows     , stage.in1)
    b.connect(columns  , stage.in2)
    b.connect(normalize, stage.in3)
    stage.out
  }

  private final val name = "GramSchmidtMatrix"

  private type Shp = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.rows"     ),
      in2 = InI (s"$name.columns"  ),
      in3 = InI (s"$name.normalize"),
      out = OutD(s"$name.out"      )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends FilterWindowedInAOutA[Double, BufD, Shp](name, layer, shape)(shape.in0, shape.out) {

    private[this] val hRows     = InIAux  (this, shape.in1)(max(1, _))
    private[this] val hColumns  = InIAux  (this, shape.in2)(max(1, _))
    private[this] val hNorm     = InIAux  (this, shape.in3)()

    private[this] var dotBuf    : Array[Double] = _
    private[this] var rows      : Int           = _
    private[this] var columns   : Int           = _
    private[this] var winSize   : Int           = _
    private[this] var normalize : Boolean       = _

    protected def tryObtainWinParams(): Boolean = {
      val ok = hRows.hasNext && hColumns.hasNext && hNorm.hasNext
      if (ok) {
        val oldSize = winSize
        rows      = hRows   .next()
        columns   = hColumns.next()
        normalize = hNorm   .next() > 0
        winSize   = rows * columns
        if (winSize != oldSize) {
//          winBuf = new Array(winSize)
          dotBuf = new Array(rows   )
        }
      }
      ok
    }

    protected def winBufSize: Int = winSize

    override protected def stopped(): Unit = {
      super.stopped()
      dotBuf = null
    }

    /*
  def dot(u: Vector[Double], v: Vector[Double]): Double = (u, v).zipped.map(_ * _).sum

  def proj(u: Vector[Double], v: Vector[Double]): Vector[Double] = u * (dot(v, u) / dot(u, u))

  def loop(vt: Vector[Vector[Double]], res: Vector[Vector[Double]]): Vector[Vector[Double]] =
    vt match {
      case vk +: vtt =>
        val uk = (vk /: res) { case (ukp, up) =>
          // ukp - proj(up, ukp)
          val f = dot(ukp, up) / dot(up, up)  // idea: cache dot(up, up) -> `dotBuf`
          ukp - up * f
        }
        loop(vtt, res :+ uk)

      case _ => res
    }

     */

    // cf. https://en.wikipedia.org/wiki/Gram%E2%80%93Schmidt_process#Numerical_stability
    // cf. https://gist.github.com/Sciss/e9ed09f4e1e06b4fe379b16378fb5bb5
    protected def processWindow(): Unit = {
//      val a     = winBuf
//      val size  = winSize
//      if (writeToWinOff < size) {
//        val writeOffI = writeToWinOff.toInt
//        Util.clear(a, writeOffI, size - writeOffI)
//      }

      val _rows = rows
      val _cols = columns
      val b     = winBuf
      var i = 0
      // calculate each output vector
      // by replacing the row in `winBuf` in-place, i.e. v -> u
      while (i < _rows) {
        val ukOff = i * _cols
        var j = 0
        while (j < i) {
          val dotUU = dotBuf(j)
          if (dotUU > 0) {
            var dotVU = 0.0
            var k     = ukOff
            val stopK = k + _cols
            val uOff  = j * _cols
            var m     = uOff
            while (k < stopK) {
              dotVU += b(k) * b(m)
              k += 1
              m += 1
            }
            val f = -dotVU / dotUU
            k = ukOff
            m = uOff
            while (k < stopK) {
              b(k) += f * b(m)
              k += 1
              m += 1
            }
          }
          j += 1
        }
        // calc and store dotVV
        j = ukOff
        val stopJ = j + _cols
        var dotUU = 0.0
        while (j < stopJ) {
          val f = b(j)
          dotUU += f * f
          j += 1
        }
        dotBuf(i) = dotUU

        i += 1
      }

      if (normalize) {
        // "us.map(uk => uk / length(uk))"
        i = 0
        while (i < _rows) {
          val ukOff   = i * _cols
          val dotUU   = dotBuf(i)
          val length  = math.sqrt(dotUU)
          if (length > 0) {
            val f       = 1.0 / length
            var j       = ukOff
            val stopJ   = j + _cols
            while (j < stopJ) {
              b(j) *= f
              j += 1
            }
          }
          i += 1
        }
      }

//      size
    }
  }
}