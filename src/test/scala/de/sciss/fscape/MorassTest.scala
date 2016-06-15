package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.graph.GenWindow
import de.sciss.numbers
import de.sciss.synth.io.{AudioFileSpec, AudioFileType, SampleFormat}

object MorassTest extends App {
  val inputs    = (userHome / "Music" / "work").children(f => f.name.startsWith("mentasm-") && f.ext == "aif")
  val outputDir = userHome / "Documents" / "projects" / "Unlike" / "audio_work"

  println(s"There are ${inputs.size} input files.")
  outputDir.mkdir()

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
                          keepFileLength    : Boolean         = true
                   ) {
    require(inputWinSize     >= 2)
    require(templateWinSize  >= 2)
    require(stepSize         >  0 && stepSize <= inputWinSize && stepSize <= templateWinSize )
    require(radius           >= 0 && radius <= 1.0)
    require(synthesizeWinAmt >= 0 && synthesizeWinAmt <= 1.0)
  }

  def mkFourierFwd(in: File, spec: AudioFileSpec, gain: Gain,
                   truncate: Boolean): GE = {
    ???
  }

  def mkFourierInv(in: GE, out: File, spec: AudioFileSpec, gain: Gain,
                   truncate: Boolean): Unit = {
    ???
  }

  def mkMorass(config: MorassConfig): GE = {
    ???
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

  case class Gain(value: Double, normalized: Boolean = false)

  def run(inA: File, inB: File): Unit = {
    val idA = inA.base.substring(8)
    val idB = inB.base.substring(8)
    val output = outputDir / s"mentasm-$idA-$idB.aif"
    if (!output.exists()) {
      println(s"Processing $idA - $idB...")

      val fftA = mkFourierFwd(in = inA,
        spec = OutputSpec.aiffFloat, gain = Gain.normalized, truncate = false)
      val fftB = mkFourierFwd(in = inB,
        spec = OutputSpec.aiffFloat, gain = Gain.normalized, truncate = false)

      import graph._
      val fftAZ = UnzipWindow(fftA)
      val fftBZ = UnzipWindow(fftB)

      val config = MorassConfig(input = fftAZ, template = fftBZ,
        synthesizeWinType = GenWindow.Rectangle,
        inputWinSize = 4096, templateWinSize = 32768, stepSize = 16, ampModulation = 0.0675 /* 1.0 */,
        synthesizeWinAmt = 0.0625)
      val morass = mkMorass(config)
      /* val fftInv = */ mkFourierInv(in = morass, out = output,
        spec = OutputSpec.aiffInt, gain = Gain.normalized, truncate = false)
    }
  }
}
