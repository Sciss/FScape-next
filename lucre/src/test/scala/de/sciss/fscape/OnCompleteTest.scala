package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.fscape.stream.Cancelled
import de.sciss.lucre.expr.StringObj
import de.sciss.lucre.synth.InMemory
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe

import scala.util.{Failure, Success}

// XXX TODO --- should quit after action is executed
object OnCompleteTest extends App {
  type S                  = InMemory
  implicit val cursor: S  = InMemory()

//  new Thread {
//    override def run(): Unit = Thread.sleep(Long.MaxValue)
//    start()
//  }

//  RenderingImpl.DEBUG = true

  val ctl = cursor.step { implicit tx =>
    val f = FScape[S]
    val g = Graph {
      import graph.{AudioFileOut => _, _}
      import lucre.graph._
      val sig = Line(0, 1, length = 100000000).sqrt
      sig.head.poll(0, "head")
      sig.last.poll(0, "last")
      OnComplete("action")
//      Action(0, "action")
    }
    val a = proc.Action[S]()
    a.graph() = proc.Action.Graph {
      import de.sciss.lucre.expr.ExImport._
      import de.sciss.lucre.expr.graph._
      val v = "value".attr[String]("Ok")
      val actDone = for {
        fsc   <- "invoker".attr[Obj]
        name  <- fsc.attr[String]("name")
      } yield {
        PrintLn("Completed '" ++ name ++ "' with value " ++ v.toStr)
      }
      actDone.getOrElse(PrintLn("Missing data in done action"))
    }
    f.attr.put("action" , a)
    f.attr.put("name"   , StringObj.newConst[S]("line-poll"))
    f.graph() = g
    implicit val universe: Universe[S] = Universe.dummy
    val r = f.run()
    r.reactNow { implicit tx => state =>
      println(s"Rendering: $state")
      if (state.isComplete) r.result.foreach {
        case Failure(Cancelled()) =>
//          sys.exit()
        case Failure(ex) =>
          ex.printStackTrace()
          sys.exit(1)
        case Success(_) =>
//          sys.exit()
      }
    }
    r.control
  }
//  import scala.concurrent.ExecutionContext.Implicits.global
//  ctl.status.onComplete { tr =>
//    println(s"Control completed with $tr")
//  }
}