package de.sciss.fscape

import java.awt.image.BufferedImage

import de.sciss.file._

object EisenerzMedian {
  def main(args: Array[String]): Unit = {
    median()
  }

  def median(): Unit = {
    val baseDir   = userHome / "Documents" / "projects" / "Eisenerz" / "image_work6"
    val template  = baseDir / "frame-%d.jpg"
    val idxRange  = (276 to 628).map(x => x: GE)
    val numInput  = idxRange.size
    val indices   = idxRange.reduce(_ ++ _)
    val width     = 3280
    val height    = 2464
    val frameSize = width * height

    val sideLen   = 3 // 2
    val medianLen = sideLen * 2 + 1
    val thresh    = 0.2 // 0.01 // 0.05 // 0.0 // 0.3333
    //    val thresh    = 0.2 / 150

    val output    = userHome / "Documents" / "temp" / "out_median.png"

//    val fltBlur = new SmartBlurFilter
//    fltBlur.setRadius(7)
//    fltBlur.setThreshold(20)

    val images      = new Array[BufferedImage]       (medianLen)
    val luminances  = new Array[Array[Array[Double]]](medianLen)

    val g = Graph {
      import graph._

      def blur(in: GE): GE = in // XXX TODO --- perhaps 2D-FFT-based convolution --- fltBlur.filter(in, null)

      // actually "negative delay"
      def delayFrame(in: GE, n: Int = 1): GE = in.drop(frameSize * n)

      def extractBrightness(in: GE): GE = {
        val r   = in.`\\`(0) // ChannelProxy(in, 0)
        val g   = in.`\\`(1) // ChannelProxy(in, 1)
        val b   = in.`\\`(2) // ChannelProxy(in, 2)
        (0.299 * r.squared + 0.587 * g.squared + 0.114 * b.squared).sqrt
      }

      def normalize(in: GE, headroom: GE = 1): GE = {
        val max       = RunningMax(in.abs).last
        val gain      = max.reciprocal * headroom
        val buf       = BufferDisk(in)
        buf * gain
      }

      val bufIn     = ImageFileSeqIn(template = template, numChannels = 3, indices = indices)
      val blurImg   = blur(bufIn)
      val lum       = extractBrightness(blurImg)

      // XXX TODO --- or Sliding(lum, frameSize * medianLen, frameSize) ?
      val lumWin    = (Vector(lum) /: (0 until medianLen)) { case (res @ (init :+ last), _) =>
        res :+ delayFrame(last)
      }

      val lumC      = lumWin(sideLen)

    }

    /*
    val mask  = Array.ofDim[Double](height, width)
    val maskB = Array.ofDim[Double](height, width)

    val medianArr = new Array[Double](medianLen)

    for (idx <- (medianLen - 1) until numInput) {
      readOne(sorted(idx), idx = idx % medianLen)
      val cIdx = (idx - sideLen) % medianLen
      val lumC = luminances(cIdx)

      // calculate mask
      {
        var y = 0
        while (y < height) {
          var x = 0
          while (x < width) {
            val comp = lumC(y)(x)
            //            var min  = comp
            //            var max  = comp
            var i = 0
            while (i < medianLen) {
              val d = luminances(i)(y)(x)
              //              if (d < min) min = d
              //              if (d > max) max = d
              medianArr(i) = d
              i += 1
            }
            java.util.Arrays.sort(medianArr)
            val min = medianArr(0)
            val max = medianArr(medianLen - 1)
            //            mask(y)(x) = if (max - min > thresh && (comp == min || comp == max)) 1.0 else 0.0
            mask(y)(x) = if (max - min > thresh && (comp == min || comp == max)) {
              val med = medianArr(sideLen)
              comp absdif med
            } else {
              0.0
            }
            x += 1
          }
          y += 1
        }
      }
      // blur mask
      for (_ <- 0 until 3 /* 2 */) {
        var y = 0
        while (y < height) {
          var x = 0
          while (x < width) {
            var sum = 0.0
            var cnt = 0
            var yi = math.max(0, y - 1)
            val ys = math.min(height, y + 2)
            while (yi < ys) {
              var xi = math.max(0, x - 1)
              val xs = math.min(width, x + 2)
              while (xi < xs) {
                sum += mask(yi)(xi)
                cnt += 1
                xi += 1
              }
              yi += 1
            }
            maskB(y)(x) = sum / cnt
            x += 1
          }
          y += 1
        }

        // copy back for recursion
        y = 0
        while (y < height) {
          System.arraycopy(maskB(y), 0, mask(y), 0, width)
          y += 1
        }
      } // recursion

      // mix to composite
      (0 until 3).foreach { ch =>
        val chan  = extractChannel(images(cIdx), ch)
        val compC = composite(ch)
        var y = 0
        while (y < height) {
          var x = 0
          while (x < width) {
            // compC(y * width + x) += maskB(y)(x) * chan(y)(x)
            val z     = y * width + x
            val m     = maskB(y)(x)
            val in    = chan (y)(x)
            val value = m * in
            if (compC(z) < value) compC(z) = value
            x += 1
          }
          y += 1
        }
      }
    }
    */

    ???
  }
}
