package de.sciss.fscape.stream

import de.sciss.audiofile.{AudioFile, AudioFileSpec}
import de.sciss.file._
import de.sciss.transform4s.fft.DoubleFFT_1D

object PureTest extends App {
  val fIn   = userHome / "Music" / "work" / "B19h39m45s23jan2015.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  println(s"${new java.util.Date()} start")
  val afIn        = AudioFile.openRead(fIn)
  val afOut       = AudioFile.openWrite(fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100))
  val bufSize     = 8192
  val buf         = afIn.buffer(bufSize)
  var framesRead  = 0L
  val fftSize     = 1024
  val fft         = DoubleFFT_1D(fftSize)
  val fftBuf      = new Array[Double](fftSize)
  while (framesRead < afIn.numFrames) {
    val chunkSize = math.min(bufSize, afIn.numFrames - framesRead).toInt
    afIn.read(buf, 0, chunkSize)
    var i = 0
    while (i < chunkSize) {
      val c2 = math.min(fftSize, chunkSize - i)
      var k = 0
      while (k < c2) {
        fftBuf(k) = buf(0)(k + i).toDouble
        k += 1
      }
      while (k < fftSize) {
        fftBuf(k) = 0.0
        k += 1
      }
      fft.realForward(fftBuf)
      k = 0
      while (k < c2) {
        buf(0)(k + i) = fftBuf(k).toFloat
        k += 1
      }
      i += c2
    }

    framesRead += chunkSize
    afOut.write(buf, 0, chunkSize)
  }
  println(s"${new java.util.Date()} stop")
  afIn.close()
  afOut.close()
}
