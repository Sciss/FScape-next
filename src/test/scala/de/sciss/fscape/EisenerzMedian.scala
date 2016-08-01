package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.synth.io.AudioFileSpec

import scala.swing.Swing

object EisenerzMedian {
  def main(args: Array[String]): Unit = {
    median()
  }

  def median(): Unit = {
    val baseDir   = userHome / "Documents" / "projects" / "Eisenerz" / "image_work6"
    val template  = baseDir / "frame-%d.jpg"
    val idxRange  = (276 to 628) .take(30) .map(x => x: GE)
    val numInput  = idxRange.size
    val indices   = idxRange.reduce(_ ++ _)
    val width     = 3280  /2
    val height    = 2464  /2
    val frameSize = width * height

    val sideLen   = 3 // 2
    val medianLen = sideLen * 2 + 1
    val thresh    = 0.2 // 0.01 // 0.05 // 0.0 // 0.3333
    //    val thresh    = 0.2 / 150

//    val fOut      = userHome / "Documents" / "temp" / "out_median.png"
    val fOut      = userHome / "Documents" / "temp" / "out_median.jpg"

//    val fltBlur = new SmartBlurFilter
//    fltBlur.setRadius(7)
//    fltBlur.setThreshold(20)

    val config  = stream.Control.Config()
    config.blockSize  = width * 2
    config.useAsync   = false

    val g = Graph {
      import graph._

      def blur(in: GE): GE = in // XXX TODO --- perhaps 2D-FFT-based convolution --- fltBlur.filter(in, null)

      // actually "negative delay"
      def delayFrame(in: GE, n: Int = 1): GE = in.drop(frameSize * n)

      def extractBrightness(in: GE): GE = {
        val r   = ChannelProxy(in, 0)
        val g   = ChannelProxy(in, 1)
        val b   = ChannelProxy(in, 2)
        (0.299 * r.squared + 0.587 * g.squared + 0.114 * b.squared).sqrt
      }

      def quarter(in: GE): GE = {
        val half1     = ResizeWindow(in   , size = width * 2, start = width, stop = 0)
        val half2     = ResizeWindow(half1, size = width * height * 2, start = width * height, stop = 0)
        half2
      }

      def normalize(in: GE, headroom: GE = 1): GE = {
        val max       = RunningMax(in.abs).last
        val gain      = max.reciprocal * headroom
        gain.ampdb.roundTo(0.01).poll(0, "gain [dB]")
        // Plot1D(in, width * height)
        // in.poll(1.0/32, label = "test")
        val buf       = BufferDisk(in)
        buf * gain
      }

      def mkImgSeq() = {
        val res = ImageFileSeqIn(template = template, numChannels = 3, indices = indices)
        quarter(res)
      }

      val bufIn     = mkImgSeq()
//      val bufIn     = WhiteNoise(Seq.fill[GE](3)(0.5)).take(frameSize * idxRange.size)
      val blurImg   = blur(bufIn)
      val lum       = extractBrightness(blurImg)

//      Length(bufIn).poll(0, "bufIn.length")
//      Length(lum  ).poll(0, "lum  .length")
//      RunningSum(bufIn).poll(1.0/frameSize, "PING")

//      // XXX TODO --- or Sliding(lum, frameSize * medianLen, frameSize) ?
//      val lumWin    = (Vector(lum) /: (0 until medianLen)) { case (res @ (init :+ last), _) =>
//        res :+ delayFrame(last)
//      }
//
//      val lumC      = lumWin(sideLen)

      val lumSlide  = Sliding(lum, size = frameSize * medianLen, step = frameSize)
//      val lumT      = TransposeMatrix(lumSlide, columns = frameSize, rows = medianLen)
//      val comp0     = delayFrame(lum, n = sideLen)
////      val comp      = comp0.elastic((sideLen * frameSize + config.blockSize - 1) / config.blockSize)
//      val comp      = BufferDisk(comp0)
      val dly   = delayFrame(mkImgSeq(), n = sideLen).take(frameSize * (numInput - (medianLen - 1))) // .dropRight(sideLen * frameSize)
      val comp  = extractBrightness(blur(dly))

//      val runTrig   = Impulse(1.0 / medianLen)
//      val minR      = RunningMin(lumT, runTrig)
//      val maxR      = RunningMax(lumT, runTrig)
//      val meanR     = RunningSum(lumT, runTrig) / medianLen
//      val min       = Sliding(minR .drop(medianLen - 1), size = 1, step = medianLen)
//      val max       = Sliding(maxR .drop(medianLen - 1), size = 1, step = medianLen)
//      // XXX TODO --- use median instead of mean
//      val mean      = Sliding(meanR.drop(medianLen - 1), size = 1, step = medianLen)

      val medianTrig = Impulse(1.0/(frameSize * medianLen))
      val minR      = RunningWindowMin(lumSlide, size = frameSize, trig = medianTrig)
      val maxR      = RunningWindowMax(lumSlide, size = frameSize, trig = medianTrig)
      val sumR      = RunningWindowSum(lumSlide, size = frameSize, trig = medianTrig)
//      val min       = ResizeWindow(minR, size = medianLen, start = medianLen - 1, stop = 0)
      val min       = ResizeWindow(minR, size = frameSize * medianLen, start = frameSize * (medianLen - 1), stop = 0)
      val max       = ResizeWindow(maxR, size = frameSize * medianLen, start = frameSize * (medianLen - 1), stop = 0)
      val mean      = ResizeWindow(sumR, size = frameSize * medianLen, start = frameSize * (medianLen - 1), stop = 0) / medianLen

//      Length(min  ).poll(0, "min  .length")

      val maskIf    = (max - min > thresh) * ((comp sig_== min) max (comp sig_== max))
      val mask      = maskIf * {
        val med = mean // medianArr(sideLen)
        comp absdif med
      }
      val maskBlur  = blur(mask)

      // XXX TODO
      // mix to composite
      // - collect 'max' pixels for comp * maskBlur

//      lumSlide.poll(1.0/frameSize, "lumSlide")
//      lumT    .poll(1.0/frameSize, "lumT    ")
//      min     .poll(1.0/frameSize, "min     ")
//      max     .poll(1.0/frameSize, "max     ")
      maskBlur.poll(1.0/frameSize, "maskBlur")

      val sel     = maskBlur * dly
      val expose  = RunningWindowMax(sel, size = frameSize)
//      val expose  = RunningWindowSum(sel, size = frameSize)

//      val test  = min /* maskBlur */ .take(frameSize * (numInput - (medianLen - 1))).takeRight(frameSize)
      val test  = expose.take(frameSize * (numInput - (medianLen - 1))).takeRight(frameSize)
                  // min.take(frameSize * (numInput - (medianLen - 1))).takeRight(frameSize)
      val sig   = normalize(test)
      val spec  = ImageFile.Spec(width = width, height = height, numChannels = /* 1 */ 3,
        fileType = ImageFile.Type.JPG /* PNG */, sampleFormat = ImageFile.SampleFormat.Int8,
        quality = 100)
      ImageFileOut(file = fOut, spec = spec, in = sig)
      // "full resolution copy"
      AudioFileOut(file = fOut.replaceExt("aif"),
        spec = AudioFileSpec(numChannels = 3, sampleRate = 44100), in = sig)
    }

    val ctrl    = stream.Control(config)
    ctrl.run(g)

    Swing.onEDT {
      SimpleGUI(ctrl)
    }

    println("Running.")

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
 }
}
