package de.sciss.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.DoubleObj
import de.sciss.lucre.Durable
import de.sciss.lucre.store.BerkeleyDB
import de.sciss.synth.proc.SoundProcesses
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

  "An FScape object" should "be serializable" in { cursor =>
    val (fH, numSources) = cursor.step { implicit tx =>
      val f = FScape[T]
      val g = Graph {
        import graph._
        import lucre.graph._
        1.poll("rendering")
        val value = WhiteNoise(100).take(100000000).last
        MkDouble("out-1", value)
        MkDouble("out-2", value + 1)
      }
      /* val out1 = */ f.outputs.add("out-1", DoubleObj)
      /* val out2 = */ f.outputs.add("out-2", DoubleObj)
      f.graph() = g
      tx.newHandle(f) -> g.sources.size
    }

    cursor.step { implicit tx =>
      val f = fH()
      val g = f.graph.value
      assert(g.sources.size === numSources)
      val outputs = f.outputs.iterator.toList.sortBy(_.key)
      assert(outputs.size === 2)
      val out1 :: out2 :: Nil = outputs
      assert(out1.key       === "out-1")
      assert(out1.valueType === DoubleObj)
      assert(out2.key       === "out-2")
      assert(out2.valueType === DoubleObj)
    }
  }
}