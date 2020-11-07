package de.sciss.fscape
package tests

import de.sciss.fscape.Ops._

trait ReadmeExample {
  import de.sciss.audiofile.AudioFile

  {
    val fIn       = new java.io.File("input.aif")
    val specIn    = AudioFile.readSpec(fIn)
    val fOut      = new java.io.File("sonogram.png")
    val fftSize   = 8192
    val timeResMS = 4.0   // milliseconds
    val winStep   = math.min(fftSize, (timeResMS / 1000 * specIn.sampleRate + 0.5).toInt)
    val numWin    = ((specIn.numFrames - fftSize + winStep - 1) / winStep).toInt
    val numBands  = 432
    val dbMin     = -78.0
    val dbMax     = -18.0

    val g = Graph {
      import graph._
      val in        = AudioFileIn(file = fIn.toURI, numChannels = 1)
      val slid      = Sliding(in, fftSize, winStep)
      val winFun    = GenWindow(size = fftSize, shape = GenWindow.Hann)
      val windowed  = slid * winFun
      val rotWin    = RotateWindow(windowed, size = fftSize, amount = fftSize/2)
      val fft       = Real1FFT(rotWin, size = fftSize)
      val constQ    = ConstQ(fft, fftSize = fftSize, numBands = numBands)
      val norm      = constQ.ampDb.linLin(dbMin * 2, dbMax * 2, 0.0, 1.0).clip()
      val rotImg    = RotateFlipMatrix(norm, rows = numWin, columns = numBands, mode = RotateFlipMatrix.Rot90CCW)
      val specOut   = ImageFile.Spec(width = numWin, height = numBands, numChannels = 1)
      ImageFileOut(file = fOut.toURI, spec = specOut, in = rotImg)
    }

    val ctrl  = stream.Control()

    ctrl.run(g)
    import ctrl.config.executionContext
    ctrl.status.foreach { _ => sys.exit() }
  }
}
