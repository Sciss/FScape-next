package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.lucre.FScape
import de.sciss.fscape.stream.Cancelled
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.expr.IntObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.{GenContext, WorkspaceHandle}

import scala.util.{Failure, Success}

object Test extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

//  val tmp = File.createTemp()
  val tmpDir  = userHome / "Documents" / "temp"
  require(tmpDir.isDirectory)
  val tmpF    = tmpDir / "test.aif"

  val fH = cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph.{AudioFileOut => _, _}
      import lucre.graph._
      import Ops._
      val freq  = "freq".attr
      val dur   = "dur" .attr(10.0)
      val sr    = 44100.0
      val durF  = dur * sr
      val sig0  = SinOsc(freq / sr) * 0.5
      val sig   = sig0.take(durF)
      freq.poll(0, "started")
      // AudioFileOut(file = tmpF, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = sig)
      AudioFileOut("file", in = sig, sampleRate = sr)
    }
    val loc = ArtifactLocation.newConst[S](tmpDir)
    f.attr.put("file", Artifact(loc, tmpF))
    f.attr.put("freq", IntObj.newConst(441))
    f.graph() = g
    tx.newHandle(f)
  }

  import WorkspaceHandle.Implicits.dummy

  cursor.step { implicit tx =>
    val f = fH()
    implicit val ctx = GenContext[S]
    val r = f.run()
    r.reactNow { implicit tx => state =>
      println(s"Rendering: $state")
      if (state.isComplete) r.result.foreach {
        case Failure(Cancelled()) =>
          sys.exit()
        case Failure(ex) =>
          ex.printStackTrace()
          sys.exit(1)
        case Success(_) =>
          val spec = AudioFile.readSpec(tmpF)
          println(spec)
          sys.exit()
      }
    }
  }
}
