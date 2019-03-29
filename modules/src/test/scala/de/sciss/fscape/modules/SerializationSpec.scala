package de.sciss.fscape.modules

import de.sciss.fscape.Graph
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Durable
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.synth.proc.SoundProcesses
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

  def makeTuple(f: FScape[S])(implicit tx: S#Tx): (stm.Source[S#Tx, FScape[S]], Graph) =
    tx.newHandle(f) -> f.graph.value

  def testTuple(cursor: stm.Cursor[S], tuple: (stm.Source[S#Tx, FScape[S]], Graph)): Unit =
    cursor.step { implicit tx =>
      val (fH, g1) = tuple
      val f = fH()
      val g = f.graph.value
      assert(g === g1)
    }

  "Chain Gain" should "be serializable" in { implicit cursor =>
    makeTest { implicit tx => ChangeGainModule[S]() }
  }
}