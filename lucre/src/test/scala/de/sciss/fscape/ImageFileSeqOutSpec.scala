package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.graph.{ImageFile, ImageFileSeqIn}
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.{Artifact, ArtifactLocation}
import de.sciss.lucre.store.BerkeleyDB
import de.sciss.synth.proc.{Durable, SoundProcesses, Universe}
import org.scalatest.Outcome
import org.scalatest.flatspec.FixtureAnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ImageFileSeqOutSpec extends FixtureAnyFlatSpec with Matchers {
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

  "A Lucre ImageFileSeqOut" should "work" in { implicit cursor =>
    val dirOut      = File.createTemp(directory = true)
    val tempOut     = dirOut / "frame-%d.png"
    val widthIn     = 640
    val heightIn    = 480
    val numChannels = 3
    val specIn      = ImageFile.Spec(ImageFile.Type.PNG, ImageFile.SampleFormat.Int8,
      width = widthIn, height = heightIn, numChannels = numChannels)
    val metroPeriod = 10000
    val fileLen     = widthIn * heightIn
    val idxSq       = List(1, 2)

    val rendering = cursor.step { implicit tx =>
      val f = FScape[T]()
      val g = Graph {
        import graph.{ImageFileOut => _, ImageFileSeqOut => _, _}
        import lucre.graph._
        val m0: GE = Seq.fill[GE](3)(Metro(metroPeriod))
        val m  = m0.take(fileLen)
        /*val out = */ ImageFileSeqOut("out", m,
          width         = widthIn,
          height        = heightIn,
          fileType      = ImageFileOut.id(specIn.fileType),
          sampleFormat  = ImageFileOut.id(specIn.sampleFormat),
          indices       = ValueIntSeq(idxSq: _*)
        )
        // Length(out).poll(0, s"Length should be $fileLen")
      }
      f.graph() = g
      val art = Artifact[T](ArtifactLocation.newConst(dirOut), tempOut)
      f.attr.put("out", art)
      implicit val universe: Universe[T] = Universe.dummy
      f.run()
    }

    Await.result(rendering.control.status, Duration.Inf)

    for {
      idx <- idxSq
    } {
      val fOut    = ImageFileSeqIn.formatTemplate(tempOut, idx)
      val specOut = ImageFile.readSpec(fOut)

      assert(specOut.fileType     === specIn.fileType     )
      assert(specOut.width        === specIn.width        )
      assert(specOut.height       === specIn.height       )
      assert(specOut.sampleFormat === specIn.sampleFormat )
      assert(specOut.numChannels  === specIn.numChannels  )
    }
    //      val buf = specOut.buffer(fileLen)
    //      specOut.read(buf)
    //      val syn = Array.tabulate(fileLen) { i =>
    //        if (i % metroPeriod == 0) 1f else 0f
    //      }
    //      assert(buf(0) === syn)
  }
}