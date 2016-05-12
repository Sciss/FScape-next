//package de.sciss.fscape
//
//import de.sciss.file._
//import de.sciss.synth.io.AudioFileSpec
//
//object Test extends App {
//  val fIn   = userHome / "Music" / "work" / "mentasm-199a3aa1.aif"
//  val fOut  = userHome / "Music" / "work" / "_killme.aif"
//
//  val g = Graph {
//    import graph._
//    val in  = DiskIn(file = fIn)
//    val fft = Real1FFT(in, size = 1024)
//    DiskOut(file = fOut, spec = AudioFileSpec(numChannels = 1, sampleRate = 44100), in = fft)
//  }
//
//  val process = g.expand
//  process.run()
//}
