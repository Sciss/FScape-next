package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.graph.GenWindow
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}

import scala.swing.Swing
import scala.util.Random

object MorassTest extends App {
  val inputs    = (userHome / "Music" / "work").children(f => f.name.startsWith("mentasm-") && f.ext == "aif")
  val outputDir = userHome / "Documents" / "projects" / "Unlike" / "audio_work"

  println(s"There are ${inputs.size} input files.")
  outputDir.mkdir()
  run()

  case class MorassConfig(input             : GE,
                          template          : GE,
                          inputWinSize      : Int             = 16384,
                          templateWinSize   : Int             = 16384,
                          analyzeWinType    : GenWindow.Shape = GenWindow.Hann,
                          synthesizeWinType : GenWindow.Shape = GenWindow.Hann,
                          synthesizeWinAmt  : Double          = 1.0,
                          ampModulation     : Double          = 0.0,
                          stepSize          : Int             = 16,
                          radius            : Double          = 1.0,
                          numFrames: Int,
                          keepFileLength: Boolean = true
                   ) {
    require(inputWinSize     >= 2)
    require(templateWinSize  >= 2)
    require(stepSize         >  0 && stepSize <= inputWinSize && stepSize <= templateWinSize )
    require(radius           >= 0 && radius <= 1.0)
    require(synthesizeWinAmt >= 0 && synthesizeWinAmt <= 1.0)
  }

  def complexNormalize(in: GE, headroom: GE): GE = {
    val mag = in.complex.abs
    normalizeImpl(in = in, mag = mag, headroom = headroom)
  }

  def realNormalize(in: GE, headroom: GE): GE =
    normalizeImpl(in = in, mag = in.abs, headroom = headroom)

  def normalizeImpl(in: GE, mag: GE, headroom: GE): GE = {
    import graph._
    val max       = RunningMax(mag).last
    val gain      = max.reciprocal * headroom
    val buf       = BufferDisk(in)
    val sig       = buf * gain
    sig
  }

  def mkFourierFwd(in: File, size: GE, gain: Gain): GE = {
    import graph._
    val disk      = DiskIn(file = in, numChannels = 1).take(size)
    val complex   = ZipWindow(disk, DC(0.0))
    val fft       = Fourier(in = complex, size = size, dir = +1.0)
    val sig       =
      if      (gain.isUnity   ) fft
      else if (gain.normalized) complexNormalize(fft, headroom = gain.value)
      else                      fft * gain.value
    sig
  }

  def mkFourierInv(in: GE, size: GE, out: File, spec: AudioFileSpec, gain: Gain): Unit = {
    import graph._
    val iFft  = Fourier(in = in, size = size, dir = -1.0)
    val re    = ChannelProxy(UnzipWindow(iFft), 0)
    val sig   =
      if     (gain.isUnity   ) re
      else if(gain.normalized) realNormalize(re, headroom = gain.value)
      else                     re * gain.value
    DiskOut(file = out, spec = spec, in = sig)
  }

  def mkMorass(config: MorassConfig): GE = {
    import graph._
    import config._

    val winSize       = math.max(inputWinSize, templateWinSize)
    val winAnaIn      = GenWindow(size = inputWinSize   , shape = analyzeWinType)
    val winAnaTemp    = GenWindow(size = templateWinSize, shape = analyzeWinType)
    val winSynth      = if (synthesizeWinAmt == 1.0) {
      GenWindow(size = inputWinSize, shape = synthesizeWinType)
    } else {
      // synthesizeWinAmt * inputWinSize is the actual window function.
      // the left half of the window and the right half of the window
      // will be split and pushed to the inputWinSize buffer's boundaries,
      // and the center will be kept at one. Like so:
      //             ________
      //    /\  >>  /        \
      //   /  \    /          \
      //  /    \  /            \
      //
      val len  = (synthesizeWinAmt * inputWinSize + 0.5).toInt
      val lenH = len >> 1
      val arr = GenWindow(size = len, shape = synthesizeWinType)
      // GenWindow.Rectangle.fill(arr, lenH, inputWinSize - len)
      ???
    }

    val fftSize   = winSize
    val winSizeH  = winSize >> 1
    val radiusI   = math.max(1, math.min(winSizeH - 1, (radius * winSizeH + 0.5).toInt))

    val slideA    = Sliding(in = input   , size = inputWinSize   , step = stepSize)
    val slideB    = Sliding(in = template, size = templateWinSize, step = stepSize)
    val winA      = slideA * winAnaIn
    val winB      = slideB * winAnaTemp

    // XXX TODO --- should use Real1FFT when the DC-packing is solved
    val fftA      = Real1FullFFT(in = winA, size = fftSize)
    val fftB      = Real1FullFFT(in = winB, size = fftSize)
    val conjA     = fftA .complex.conj   // XXX TODO -- is there a reason we take the conj of A and not B?
    val conv      = conjA.complex * fftB
    val convMag   = conv .complex.mag.reciprocal
    val elemNorm  = conv * ZipWindow(convMag, convMag)    // XXX TODO - should have a `Repeat(convMag, 2)` function
    val iFFT      = Real1FullIFFT(in = elemNorm, size = fftSize)

    val prod      = PeakCentroid1D(in = iFFT, size = fftSize, radius = radiusI)
    val transX    = prod.translate.roundTo(1)
    val shiftX    = /* if (keepFileLength) transX else */ transX + winSizeH

    val amp       = (ampModulation: GE).linlin(0, 1, 1.0, prod.peak)
    val ampPad    = RepeatWindow(in = amp   , num = winSize)
    val shiftXPad = RepeatWindow(in = shiftX, num = winSize)
    val synth     = slideA * ampPad * winSynth
    // XXX TODO --- the first window will not be shifted.
    // So a real solution would be to prepend one empty
    // window and drop it later.
    val prepend   = DC(0.0).take(winSize)
    val lapIn     = prepend ++ synth
    // XXX TODO --- probably have to introduce some buffer here
    val lap       = OverlapAdd(in = lapIn, size = winSize, step = shiftXPad + stepSize)
    val sig       = if (!keepFileLength) lap else lap.drop(winSizeH) // .take()

    sig
  }

  import numbers.Implicits._

  object Gain {
    val immediate  = Gain( 0.0.dbamp, normalized = false)
    val normalized = Gain(-0.2.dbamp, normalized = true )
  }

  object OutputSpec {
    val aiffFloat = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, 1, 44100.0)
    // numCh, sr not used
    val aiffInt   = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Int24, 1, 44100.0)
  }

  case class Gain(value: Double, normalized: Boolean = false) {
    def isUnity: Boolean = !normalized && value == 1.0
  }


  def run(): Unit = {
    val Seq(inA, inB) = Random.shuffle(inputs.combinations(2)).next()
    run(inA, inB)
    // run(inB, inA)
  }

  def run(inA: File, inB: File): Unit = {
    val idA = inA.base.substring(8)
    val idB = inB.base.substring(8)
    val output = outputDir / s"mentasm-$idA-$idB.aif"
    if (!output.exists()) {
      println(s"Processing $idA - $idB...")

      val numFramesA = AudioFile.readSpec(inA).numFrames.toInt
      val numFramesB = AudioFile.readSpec(inB).numFrames.toInt
      import numbers.Implicits._
      val truncate  = false
      val fftSizeA  = if (truncate) (numFramesA + 1).nextPowerOfTwo / 2 else numFramesA.nextPowerOfTwo
      val fftSizeB  = if (truncate) (numFramesB + 1).nextPowerOfTwo / 2 else numFramesB.nextPowerOfTwo

      showStreamLog = true

      val g = Graph {
        import graph._

//        val fftA = mkFourierFwd(in = inA, size = fftSizeA, gain = Gain.normalized)
//        val fftB = mkFourierFwd(in = inB, size = fftSizeB, gain = Gain.normalized)
//
//        val fftAZ = UnzipWindow(fftA) // treat Re and Im as two channels
//        val fftBZ = UnzipWindow(fftB) // treat Re and Im as two channels

        val fftAZ = SinOsc(1.0/64).take(44100 * 10)
        val fftBZ = SinOsc(1.0/64).take(44100 * 10)

        val numFrames = math.min(fftSizeA, fftSizeB)
        assert(numFrames.isPowerOfTwo)

        val config = MorassConfig(input = fftAZ, template = fftBZ,
          synthesizeWinType = GenWindow.Rectangle,
          inputWinSize = 4096, templateWinSize = 32768, stepSize = 16, ampModulation = 0.0675 /* 1.0 */,
          synthesizeWinAmt = 1.0 /* XXX TODO: 0.0625 */,
          numFrames = numFrames)
        val morass = mkMorass(config)
        val morassZ = ZipWindow(ChannelProxy(morass, 0), ChannelProxy(morass, 1))

        morassZ.poll()

//        mkFourierInv(in = morassZ, size = numFrames, out = output,
//          spec = OutputSpec.aiffInt, gain = Gain.normalized)
      }

      val config = stream.Control.Config()
      config.useAsync = false // for debugging
      val ctrl = stream.Control(config)
      ctrl.run(g)

      Swing.onEDT {
        SimpleGUI(ctrl)
      }

      println("Running.")
    }
  }
}