package de.sciss.fscape.modules

import de.sciss.fscape.{Graph => FGraph}
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Durable
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.synth.proc.Widget.{Graph => WGraph}
import de.sciss.synth.proc.{SoundProcesses, Widget}
import org.scalatest.{Matchers, Outcome, fixture}

class SerializationSpec extends fixture.FlatSpec with Matchers {
  type S = Durable
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

  def makeTest(f: S#Tx => FScape[S])(implicit cursor: stm.Cursor[S]): Unit = {
    val tuple = cursor.step { implicit tx =>
      makeTuple(f(tx))
    }
    testTuple(cursor, tuple)
  }

  def makeTuple(f: FScape[S])(implicit tx: S#Tx): (stm.Source[S#Tx, FScape[S]], FGraph) =
    tx.newHandle(f) -> f.graph.value


  def testTuple(cursor: stm.Cursor[S], tuple: (stm.Source[S#Tx, FScape[S]], FGraph)): Unit =
    cursor.step { implicit tx =>
      val (fH, g1) = tuple
      val f = fH()
      val g = f.graph.value
      assert(g === g1)
    }

  def makeUITest(f: S#Tx => Widget[S])(implicit cursor: stm.Cursor[S]): Unit = {
    val tuple = cursor.step { implicit tx =>
      makeUITuple(f(tx))
    }
    testUITuple(cursor, tuple)
  }


  def testUITuple(cursor: stm.Cursor[S], tuple: (stm.Source[S#Tx, Widget[S]], WGraph)): Unit =
    cursor.step { implicit tx =>
      val (wH, g1) = tuple
      val w = wH()
      val g = w.graph.value
      assert(g === g1)
    }

  def makeUITuple(w: Widget[S])(implicit tx: S#Tx): (stm.Source[S#Tx, Widget[S]], WGraph) =
    tx.newHandle(w) -> w.graph.value

  MakeWorkspace.list.foreach { m =>
    m.name should "be serializable" in { implicit cursor =>
      makeTest { implicit tx => m.apply[S]() }
    }
  }
}