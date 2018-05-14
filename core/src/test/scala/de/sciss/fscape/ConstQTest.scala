package de.sciss.fscape

import de.sciss.file._
import de.sciss.synth.io.AudioFile

object ConstQTest extends App {
  val fIn       = file("/data/temp/sweep_inputCut.aif")
  val specIn    = AudioFile.readSpec(fIn)
  val fOut      = file("/data/temp/sweep_inputCut-sonogram.png")
  val fftSize   = 8192
  val timeResMS = 4.0   // milliseconds
  val winStep   = math.min(fftSize, (timeResMS / 1000 * specIn.sampleRate + 0.5).toInt)
  val numWin    = ((specIn.numFrames - fftSize + winStep - 1) / winStep).toInt
  val numBands  = 432
  val dbMin     = -78.0
  val dbMax     = -18.0

  val g = Graph {
    import graph._
    val in        = AudioFileIn(file = fIn, numChannels = 1)
    val slid      = Sliding(in, fftSize, winStep)
    val winFun    = GenWindow(size = fftSize, shape = GenWindow.Hann)
    val windowed  = slid * winFun
    val rotWin    = windowed  // XXX TODO: RotateWindow
    val fft       = Real1FFT(rotWin, size = fftSize)
    val constQ    = ConstQ(fft, fftSize = fftSize, numBands = numBands)
    val norm      = constQ.ampDb.linLin(dbMin * 2, dbMax * 2, 0.0, 1.0).clip()
    val rotImg    = RotateFlipMatrix(norm, rows = numWin, columns = numBands, mode = RotateFlipMatrix.Rot90CCW)
    val specOut   = ImageFile.Spec(width = numWin, height = numBands, numChannels = 1, fileType = ImageFile.Type.PNG)
    ImageFileOut(file = fOut, spec = specOut, in = rotImg)
  }

  val ctrl  = stream.Control()

  ctrl.run(g)
  println("Running.")
  import ctrl.config.executionContext
  ctrl.status.foreach { _ =>
    println("Done.")
    sys.exit()
  }
}
