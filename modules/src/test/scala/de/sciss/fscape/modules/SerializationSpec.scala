package de.sciss.fscape.modules

import de.sciss.fscape.{Graph => FGraph}
import de.sciss.lucre.{Cursor, Durable, Source}
import de.sciss.lucre.store.BerkeleyDB
import de.sciss.synth.proc.Widget.{Graph => WGraph}
import de.sciss.synth.proc.{FScape, SoundProcesses, Widget}
import org.scalatest.Outcome
import org.scalatest.flatspec.FixtureAnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SerializationSpec extends FixtureAnyFlatSpec with Matchers {
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

  def makeTest(f: T => FScape[T])(implicit cursor: Cursor[T]): Unit = {
    val tuple = cursor.step { implicit tx =>
      makeTuple(f(tx))
    }
    testTuple(cursor, tuple)
  }

  def makeTuple(f: FScape[T])(implicit tx: T): (Source[T, FScape[T]], FGraph) =
    tx.newHandle(f) -> f.graph.value

  def testTuple(cursor: Cursor[T], tuple: (Source[T, FScape[T]], FGraph)): Unit =
    cursor.step { implicit tx =>
      val (fH, g1) = tuple
      val f = fH()
      val g = f.graph.value
      assert(g === g1)
    }

  def makeUITest(f: T => Widget[T])(implicit cursor: Cursor[T]): Unit = {
    val tuple = cursor.step { implicit tx =>
      makeUITuple(f(tx))
    }
    testUITuple(cursor, tuple)
  }


  def testUITuple(cursor: Cursor[T], tuple: (Source[T, Widget[T]], WGraph)): Unit =
    cursor.step { implicit tx =>
      val (wH, g1) = tuple
      val w = wH()
      val g = w.graph.value
      assert(g === g1)
    }

  def makeUITuple(w: Widget[T])(implicit tx: T): (Source[T, Widget[T]], WGraph) =
    tx.newHandle(w) -> w.graph.value

  MakeWorkspace.list.foreach { m =>
    m.name should "be serializable" in { implicit cursor =>
      makeTest { implicit tx => m.apply[T]() }
    }
  }
}