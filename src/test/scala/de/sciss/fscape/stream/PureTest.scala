package de.sciss.fscape.stream

import de.sciss.file._
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

object PureTest extends App {
  val fIn   = userHome / "Music" / "work" / "B19h39m45s23jan2015.wav"
  val fOut  = userHome / "Music" / "work" / "_killme.aif"

  println(s"${new java.util.Date()} start")
  val afIn  = AudioFile.openRead(fIn)
  val afOut = AudioFile.openWrite(fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100))
  val bufSize = 8192
  val buf   = afIn.buffer(bufSize)
  var framesRead = 0L
  while (framesRead < afIn.numFrames) {
    val chunkSize = math.min(bufSize, afIn.numFrames - framesRead).toInt
    afIn.read(buf, 0, chunkSize)
    framesRead += chunkSize
    afOut.write(buf, 0, chunkSize)
  }
  println(s"${new java.util.Date()} stop")
  afIn.close()
  afOut.close()
}
