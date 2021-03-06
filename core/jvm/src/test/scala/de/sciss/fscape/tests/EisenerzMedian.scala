package de.sciss.fscape
package tests

import de.sciss.audiofile.AudioFileSpec
import de.sciss.file._
import de.sciss.fscape.Ops._
import de.sciss.fscape.gui.SimpleGUI

import scala.swing.Swing

object EisenerzMedian {
  def any2stringadd: Any = ()

  def main(args: Array[String]): Unit = {
    median()
  }

  def median(): Unit = {
    val SEQUENCE      = true

    val baseDirIn     = file("/") / "data" / "projects" / "Eisenerz" / "image_work6"
    val templateIn    = baseDirIn / "frame-%d.jpg"
    val baseDirOut    = file("/") / "data" / "temp"
    val templateOut   = baseDirOut / "frame-%d.jpg"
    val idxRange0     = 276 to 628
    val idxRange      = (if (SEQUENCE) idxRange0 else idxRange0.take(30)).map(x => x: GE)
    val numInput      = idxRange.size
    val indices       = idxRange.reduce(_ ++ _)   // XXX TODO --- we need a better abstraction for this
    val widthIn       = 3280
    val heightIn      = 2464
    val width         = 1920
    val height        = 1080
    val trimLeft      = 840
    val trimTop       = 720
    val gain          = 4.5 // 9.0
    val gamma         = 1.2 // 1.4
    val seqLen        = 30
    val trimRight     = widthIn  - width  - trimLeft
    val trimBottom    = heightIn - height - trimTop
//    val frameSizeIn   = widthIn * heightIn
    val frameSize     = width * height
    require(trimLeft >= 0 && trimRight >= 0 && trimTop >= 0 && trimBottom >= 0)

    val sideLen       = 3 // 2
    val medianLen     = sideLen * 2 + 1
    val thresh        = 0.2 // 0.01 // 0.05 // 0.0 // 0.3333
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
      def delayFrame(in: GE, n: Int): GE = in.drop(frameSize * n)

      def extractBrightness(in: GE): GE = {
        val r   = in.out(0)
        val g   = in.out(1)
        val b   = in.out(2)
        (0.299 * r.squared + 0.587 * g.squared + 0.114 * b.squared).sqrt
      }

      def quarter(in: GE): GE = {
        val half1 = ResizeWindow(in   , size = widthIn         , start = trimLeft       , stop = -trimRight)
        // strange-artifact-1: use `size = frameSizeIn`, `trimLeft = 400`, `trimTop = 400`.
        val half2 = ResizeWindow(half1, size = width * heightIn, start = width * trimTop, stop = -width * trimBottom)
        half2
      }

      def normalize(in: GE, headroom: GE = 1): GE = {
        val max       = RunningMax(in.abs).last
        val gain      = max.reciprocal * headroom
        gain.ampDb.roundTo(0.01).poll(0, "gain [dB]")
        // Plot1D(in, width * height)
        // in.poll(1.0/32, label = "test")
        val buf       = BufferDisk(in)
        buf * gain
      }

      def mkImgSeq() = {
        val res = ImageFileSeqIn(template = templateIn.toURI, numChannels = 3, indices = indices)
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

      val medianTrig = Metro(medianLen) // frameSize.toLong * medianLen)
      val minR      = RunningWindowMin(lumSlide, size = frameSize, gate = medianTrig)
      val maxR      = RunningWindowMax(lumSlide, size = frameSize, gate = medianTrig)
      val sumR      = RunningWindowSum(lumSlide, size = frameSize, gate = medianTrig)
//      val min       = ResizeWindow(minR, size = medianLen, start = medianLen - 1, stop = 0)
      val min       = ResizeWindow(minR, size = frameSize * medianLen, start = frameSize * (medianLen - 1), stop = 0)
      val max       = ResizeWindow(maxR, size = frameSize * medianLen, start = frameSize * (medianLen - 1), stop = 0)
      val mean      = ResizeWindow(sumR, size = frameSize * medianLen, start = frameSize * (medianLen - 1), stop = 0) / medianLen

//      Length(min  ).poll(0, "min  .length")

      val maskIf    = (max - min > thresh) * ((comp sig_== min) max (comp sig_== max))
      val mask      = maskIf * {
        val med = mean // medianArr(sideLen)
        comp absDif med
      }
      val maskBlur  = blur(mask)

      // XXX TODO
      // mix to composite
      // - collect 'max' pixels for comp * maskBlur

//      lumSlide.poll(1.0/frameSize, "lumSlide")
//      lumT    .poll(1.0/frameSize, "lumT    ")
//      min     .poll(1.0/frameSize, "min     ")
//      max     .poll(1.0/frameSize, "max     ")
      maskBlur.poll(frameSize, "maskBlur")

      val sel     = maskBlur * dly
//      val expose  = RunningWindowMax(sel, size = frameSize)
      val expose  = RunningWindowSum(sel, size = frameSize)

//      val test  = min /* maskBlur */ .take(frameSize * (numInput - (medianLen - 1))).takeRight(frameSize)

      if (SEQUENCE) {
//        val selDly      = delayFrame(sel, n = seqLen)
//        val exposeDly   = RunningWindowSum(selDly, size = frameSize)
        val exposeDly = delayFrame(expose, n = seqLen)
//        val dlyElastic  = (seqLen * frameSize) / config.blockSize + 1
//        val exposeSlid  = expose.elastic(dlyElastic) - exposeDly
        // OutOfMemoryError -- buffer to disk instead
        val exposeSlid  = exposeDly - BufferDisk(expose)
        val sig         = (exposeSlid * gain).clip(0.0, 1.0).pow(gamma)
        val spec  = ImageFile.Spec(width = width, height = height, numChannels = /* 1 */ 3,
          fileType = ImageFile.Type.JPG /* PNG */, sampleFormat = ImageFile.SampleFormat.Int8,
          quality = 100)
        val indicesOut = indices.drop(sideLen).take(idxRange.size - (medianLen - 1))
        ImageFileSeqOut(template = templateOut.toURI, spec = spec, in = sig, indices = indicesOut)

      } else {
        val last  = expose.take(frameSize * (numInput - (medianLen - 1))).takeRight(frameSize)
        // min.take(frameSize * (numInput - (medianLen - 1))).takeRight(frameSize)
        val sig   = normalize(last)
        val spec  = ImageFile.Spec(width = width, height = height, numChannels = /* 1 */ 3,
          fileType = ImageFile.Type.JPG /* PNG */, sampleFormat = ImageFile.SampleFormat.Int8,
          quality = 100)
        ImageFileOut(file = fOut.toURI, spec = spec, in = sig)
        // "full resolution copy"
        AudioFileOut(file = fOut.replaceExt("aif").toURI,
          spec = AudioFileSpec(numChannels = 3, sampleRate = 44100), in = sig)
      }
    }

    val ctrl    = stream.Control(config)
    ctrl.run(g)

    Swing.onEDT {
      SimpleGUI(ctrl)
    }

    println("Running.")
 }
}