package de.sciss.fscape

import de.sciss.file._
import de.sciss.filecache.Limit
import de.sciss.fscape.lucre.{Cache, FScape}
import de.sciss.lucre.expr.IntObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.{AudioCue, AuralSystem, GenView, Proc, Transport}

object FeedIntoAuralProcTest extends App {
  type S                  = InMemory
  implicit val cursor: S  = InMemory()

  FScape.init()
  GenView.addFactory(FScape.genViewFactory())
  val folder = userHome / "Documents" / "temp" / "fscape_test" // File.createTemp(directory = true)
  folder.mkdir()
  Cache.init(folder = folder, capacity = Limit())

  val as = AuralSystem()

  def run()(implicit tx: S#Tx): Unit = {
    val f   = FScape[S]
    val gF  = Graph {
      import graph._
      import lucre.graph._
      1.poll(0, label = "rendering")
      val value = WhiteNoise(100).take(44100L * 10)
      val mx    = RunningMax(value.abs).last
      MkAudioCue("noise", value)
      MkInt     ("max"  , mx   )
    }
    val outNoise  = f.outputs.add("noise", AudioCue.Obj)
    val outMax    = f.outputs.add("max"  , IntObj)
    f.graph() = gF

    import de.sciss.lucre.stm.WorkspaceHandle.Implicits.dummy
//    implicit val genCtx = GenContext[S]

    val p = Proc[S]
    val gP = SynthGraph {
      import synth.{doubleNumberWrapper, proc, ugen}
      import proc.graph._
      import Ops._
      import ugen.{DiskIn => _, _}
//      val b   = Buffer("noise")
      val mx  = "max".kr
      mx.poll(0, "maximum")
//      val in = PlayBuf.ar(1, b)
      val in = DiskIn.ar("noise", loop = 1)
      val sig = in / mx * -0.2.dbAmp
      Out.ar(0, Pan2.ar(sig))
    }
    p.graph() = gP

    p.attr.put("noise", outNoise)
    p.attr.put("max"  , outMax  )

    val t = Transport[S](as)
    t.addObject(p)
    t.play()
  }

  cursor.step { implicit tx =>
    as.whenStarted { _ =>
      cursor.step { implicit tx =>
        println("Run.")
        run()
      }
    }
    as.start()
  }

  new Thread {
    override def run(): Unit = Thread.sleep(Long.MaxValue)
    start()
  }
}