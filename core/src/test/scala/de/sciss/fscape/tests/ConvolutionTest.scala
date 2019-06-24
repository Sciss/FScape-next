package de.sciss.fscape
package tests

import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ConvolutionTest extends App {
//  showStreamLog = true
//  stream.Convolution.DEBUG_FORCE_FFT = true
//  stream.Convolution.DEBUG_FORCE_TIME = true

//  val g = Graph {
//    import graph._
//    val dirac: GE = DC(1.0).take(3) ++ DC(0.0).take(3)
//    //    val dirac: GE = DC(1.0).take(1) ++ DC(0.0).take(3)
//    //    val dirac: GE = DC(0.0).take(1) ++ DC(1.0).take(3) ++ DC(0.0).take(2)
//    //    val dirac: GE = DC(0.0).take(1) ++ DC(1.0).take(1) ++ DC(0.0).take(1)
//    val conv = Convolution(RepeatWindow(dirac, 6, 4), dirac, kernelLen = 3)
//    //    val conv = Convolution(dirac, dirac, kernelLen = 3)
//    Length(conv).poll(0, "length")
//    //    RepeatWindow(dirac).poll(Metro(2), "in  ")
//    RepeatWindow(conv ).poll(Metro(2), "conv")
//    Plot1D(conv, size = 100)
//  }

//  val g = Graph {
//    import graph._
//    val input : GE = DC(0.0).take(17) ++ DC(1.0).take(1)
////    val input : GE = /*DC(0.0).take(3) ++*/ 1.0 // DC(1.0).take(1)
//    val kernelLen = 18
//    def kernel: GE = SinOsc(1.0/16).take(kernelLen)
//    val conv = Convolution(input, kernel, kernelLen = kernelLen)
//    Length(conv).poll(0, "length")
////    conv.poll(0, "foo")
////    RepeatWindow(conv).poll(Metro(2), "conv")
//    Plot1D(conv, size = 100)
//  }

//  val g = Graph {
//    import graph._
//    //    val input : GE = DC(1.0).take(1) ++ DC(0.0).take(1) // Metro(18).take(18 * 1)
//    val input : GE = Metro(18).take(18 * 5)
//    //    val input : GE = /*DC(0.0).take(3) ++*/ 1.0 // DC(1.0).take(1)
//    val kernelLen = ArithmSeq(1) // , length = 3)
//    def kernel: GE = SinOsc(1.0/16).tail // .take(2) // .take(kernelLen)
//    val conv = Convolution(input, kernel, kernelLen = kernelLen, kernelUpdate = Metro(18, 1)) // Metro(18, 1))
//    Length(conv).poll(0, "length")
//    //    conv.poll(0, "foo")
//    //    RepeatWindow(conv).poll(Metro(2), "conv")
//    Plot1D(conv, size = 100)
//  }

//  val g = Graph {
//    import graph._
////    val input : GE = DC(1.0).take(1) ++ DC(0.0).take(1) // Metro(18).take(18 * 1)
//    val input : GE = Metro(18).take(18 * 5)
//    //    val input : GE = /*DC(0.0).take(3) ++*/ 1.0 // DC(1.0).take(1)
//    val kernelLen = ArithmSeq(1) // , length = 3)
//    def kernel: GE = SinOsc(1.0/16).tail //.take(20) // .take(kernelLen)
//    val conv = Convolution(input, kernel, kernelLen = kernelLen, kernelUpdate = Metro(18, 1)) // Metro(18, 1))
//    Length(conv).poll(0, "length")
//    //    conv.poll(0, "foo")
//    //    RepeatWindow(conv).poll(Metro(2), "conv")
//    Plot1D(conv, size = 100)
//  }

  val g = Graph {
    import graph._
    val sr = 44100
//    val input : GE = Metro(sr/2).take(sr * 4)
    val input : GE = WhiteNoise(0.01).take(sr * 4)
    val kernelLen = (sr * 0.1).toInt
    val freq = RepeatWindow(ArithmSeq(60).midiCps, num = kernelLen)
    def kernel: GE = SinOsc(freq/sr) * GenWindow(kernelLen, GenWindow.Hann)
    val conv = Convolution(input, kernel, kernelLen = kernelLen, kernelUpdate = Metro(kernelLen, 1))
    Length(conv).poll(0, "length")
    //    conv.poll(0, "foo")
    //    RepeatWindow(conv).poll(Metro(2), "conv")
//    Plot1D(conv, size = 100)
    import de.sciss.file._
    AudioFileOut(conv, userHome / "Documents" / "test.aif", AudioFileSpec(numChannels = 1, sampleRate = sr))
  }

  val cfg   = stream.Control.Config()
//  cfg.blockSize = 8 // XXX TODO produces problems: 5
  val ctrl  = stream.Control(cfg)

//  Swing.onEDT {
//    SimpleGUI(ctrl)
//  }

  ctrl.run(g)
  Await.result(ctrl.status, Duration.Inf)
  sys.exit()
}