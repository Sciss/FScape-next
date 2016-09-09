package de.sciss.fscape

import de.sciss.file.File
import de.sciss.fscape.FScape.Rendering
import de.sciss.fscape.stream.Cancelled
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.io.AudioFileSpec

object Test extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

  val tmp = File.createTemp()

  val fH = cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph._
      import Ops._
      val freq  = "freq".attr
      val sr    = 44100.0
      val sig   = SinOsc(sr / freq) * 0.5
      AudioFileOut(file = tmp, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = sig)
    }
    f.graph() = g
    tx.newHandle(f)
  }

  cursor.step { implicit tx =>
    val f = fH()
    val r = f.run()
    r.react { implicit tx => state =>
      println(s"Rendering: $state")
      state match {
        case Rendering.Failure(Cancelled()) =>
        case Rendering.Failure(ex) => ex.printStackTrace()
        case _ =>
      }
    }
  }
}
