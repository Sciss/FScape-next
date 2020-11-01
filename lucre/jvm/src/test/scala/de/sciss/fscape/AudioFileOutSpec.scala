package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.store.BerkeleyDB
import de.sciss.lucre.{Artifact, ArtifactLocation}
import de.sciss.audiofile.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}
import de.sciss.synth.proc.{Durable, SoundProcesses, Universe}
import org.scalatest.Outcome
import org.scalatest.flatspec.FixtureAnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AudioFileOutSpec extends FixtureAnyFlatSpec with Matchers {
  type S = Durable
  type T = Durable.Txn
  type FixtureParam = S

  SoundProcesses.init()
  FScape        .init()

  protected def withFixture(test: OneArgTest): Outcome = {
    val store  = BerkeleyDB.tmp()
    val system = Durable(store)
    try {
      test(system)
    } finally {
      system.close()
    }
  }

//  showStreamLog = true

  "A Lucre AudioFileOut" should "work" in { implicit cursor =>
    val fOut        = File.createTemp(suffix = ".wav")
    val spec        = AudioFileSpec(AudioFileType.Wave, SampleFormat.Int24, numChannels = 1, sampleRate = 48000.0)
    val metroPeriod = 10000
    val fileLen     = metroPeriod * 10

    val rendering = cursor.step { implicit tx =>
      val f = FScape[T]()
      val g = Graph {
        import graph.{AudioFileOut => _, _}
        import lucre.graph._
        val m   = Metro(metroPeriod).take(fileLen)
        /*val out = */ AudioFileOut("out", m,
          fileType      = AudioFileOut.id(spec.fileType),
          sampleFormat  = AudioFileOut.id(spec.sampleFormat),
          sampleRate    = spec.sampleRate
        )
//        Length(out).poll(0, s"Length should be $fileLen")
      }
      f.graph() = g
      val art = Artifact[T](ArtifactLocation.newConst(fOut.parent), fOut)
      f.attr.put("out", art)
      implicit val universe: Universe[T] = Universe.dummy
      f.run()
    }

    Await.result(rendering.control.status, Duration.Inf)

    val af = AudioFile.openRead(fOut)
    try {

      assert(af.fileType      === spec.fileType)
      assert(af.sampleFormat  === spec.sampleFormat)
      assert(af.numChannels   === spec.numChannels)
      assert(af.numFrames     === fileLen)

      val buf = af.buffer(fileLen)
      af.read(buf)
      val syn = Array.tabulate(fileLen) { i =>
        if (i % metroPeriod == 0) 1f else 0f
      }
      assert(buf(0) === syn)

    } finally {
      af.cleanUp()
    }
  }
}