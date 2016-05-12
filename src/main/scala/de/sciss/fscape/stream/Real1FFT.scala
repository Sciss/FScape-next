package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D

object Real1FFT {
  def apply(in: Signal[Double], size: Int /* Signal[Int] */, padding: Int /* Signal[Int] */ = 0)
           (implicit b: GraphDSL.Builder[NotUsed]): Signal[Double] = {

    val fftSize = size + padding
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

    // XXX TODO -- do we have to call `b.add(res)`?

    res
  }
}
