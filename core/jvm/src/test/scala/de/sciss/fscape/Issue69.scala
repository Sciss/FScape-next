package de.sciss.fscape

import de.sciss.audiofile.{AudioFile, AudioFileSpec}
import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import java.io.File
import scala.concurrent.Promise

class Issue69 extends UGenSpec {
  "Take an drop" should "not terminate early" in {
    val p   = Promise[Vec[Int]]()
    val f   = File.createTempFile("temp", ".aif")
    val uri = f.toURI
    val af  = AudioFile.openWrite(f, AudioFileSpec(numChannels = 2, sampleRate = 44100.0))
    val N   = 9000
    val M   = 8000
    val P   = 1
    val EXP = N - M - P
    val b   = af.buffer(N)
    af.write(b)
    af.close()
    val g = Graph {
      import graph._
      def mkIn()  = AudioFileIn(uri, numChannels = 2)
      val inA     = mkIn()
      val inB     = mkIn()
      val inAD    = inA .drop(P)
      val seg0    = inAD.take(M)
      val inADD   = inAD.drop(M)
      val len     = Length(inADD)
      DebugIntPromise(len.out(0) ++ len.out(1), p)
      val sig     = seg0 ++ inB
      DebugSink(Length(sig))
    }

    runGraph(g)
    if (!f.delete()) f.deleteOnExit()

    assert(p.isCompleted)
    val res = getPromiseVec(p)
    assert (res === Vector(EXP, EXP))
  }
}