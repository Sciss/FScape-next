package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.GraphDSL
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

object Real1FFT {
  def apply(in: Outlet /* Signal */[Double], size: Int /* Signal[Int] */, padding: Int /* Signal[Int] */ = 0)
           (implicit b: GraphDSL.Builder[NotUsed]): Outlet /* Signal */[Double] = {

    val fftSize = size + padding

    import GraphDSL.Implicits._

    val res = in.grouped(size).statefulMapConcat[Double] { () =>
      val fft = new DoubleFFT_1D(fftSize)
      val arr = new Array[Double](fftSize)

      seq =>
        var i = 0
        seq.foreach { d =>
          arr(i) = d
          i += 1
        }
        while (i < fftSize) {
          arr(i) = 0.0
          i += 1
        }
        fft.realForward(arr)
        arr.toVector
    }

    res.outlet
  }
}
