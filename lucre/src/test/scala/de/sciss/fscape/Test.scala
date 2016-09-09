package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.FScape.Rendering
import de.sciss.fscape.stream.Cancelled
import de.sciss.lucre.expr.IntObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

object Test extends App {
  implicit val cursor = InMemory()
  type S              = InMemory

//  val tmp = File.createTemp()
  val tmpF = userHome / "Documents" / "temp" / "test.aif"
  require(tmpF.parent.isDirectory)

  val fH = cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph._
      import Ops._
      val freq  = "freq".attr
      val dur   = "dur" .attr(8.0)
      val sr    = 44100.0
      val durF  = dur * sr
      val sig0  = SinOsc(sr / freq) * 0.5
      val sig   = sig0.take(durF)
      freq.poll(0, "started")
      AudioFileOut(file = tmpF, spec = AudioFileSpec(numChannels = 1, sampleRate = sr), in = sig)
    }
    f.attr.put("freq", IntObj.newConst(441))
    f.graph() = g
    tx.newHandle(f)
  }

  cursor.step { implicit tx =>
    val f = fH()
    val r = f.run()
    r.reactNow { implicit tx => state =>
      println(s"Rendering: $state")
      state match {
        case Rendering.Failure(Cancelled()) =>
        case Rendering.Failure(ex) => ex.printStackTrace()
        case Rendering.Success =>
          val spec = AudioFile.readSpec(tmpF)
          println(spec)
        case _ =>
      }
    }
  }
}
