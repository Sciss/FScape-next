/*
 *  GramSchmidtMatrix.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.stream.impl.{FilterIn4DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

object GramSchmidtMatrix {
  def apply(in: OutD, rows: OutI, columns: OutI, normalize: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in       , stage.in0)
    b.connect(rows     , stage.in1)
    b.connect(columns  , stage.in2)
    b.connect(normalize, stage.in3)
    stage.out
  }

  private final val name = "GramSchmidtMatrix"

  private type Shape = FanInShape4[BufD, BufI, BufI, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape4(
      in0 = InD (s"$name.in"       ),
      in1 = InI (s"$name.rows"     ),
      in2 = InI (s"$name.columns"  ),
      in3 = InI (s"$name.normalize"),
      out = OutD(s"$name.out"      )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn4DImpl[BufD, BufI, BufI, BufI] {

    private[this] var winBuf    : Array[Double] = _
    private[this] var dotBuf    : Array[Double] = _
    private[this] var rows      : Int           = _
    private[this] var columns   : Int           = _
    private[this] var winSize   : Int           = _
    private[this] var normalize : Boolean       = _

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = winSize
      if (bufIn1 != null && inOff < bufIn1.size) {
        rows = math.max(1, bufIn1.buf(inOff))
      }
      if (bufIn2 != null && inOff < bufIn2.size) {
        columns = math.max(1, bufIn2.buf(inOff))
      }
      if (bufIn3 != null && inOff < bufIn3.size) {
        normalize = bufIn3.buf(inOff) > 0
      }
      winSize = rows * columns
      if (winSize != oldSize) {
        winBuf = new Array(winSize)
        dotBuf = new Array(rows   )
      }
      winSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
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
    protected def processWindow(writeToWinOff: Long): Long = {
      val a     = winBuf
      val size  = winSize
      if (writeToWinOff < size) {
        val writeOffI = writeToWinOff.toInt
        Util.clear(a, writeOffI, size - writeOffI)
      }

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

      size
    }
  }
}