package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise
import scala.util.{Success, Try}

class MetroSpec extends UGenSpec {
  "The Metro UGen" should "work as specified" in {
    val lengths = List(
      0, 1, 10, 100, 1000, 1024, 1025, 10000
    )
    val phases = List(0, 1, 2)

    val sampleLen = 11000

    phases.foreach { phase =>
      lengths.foreach { n =>
        val p = Promise[Vec[Int]]()

        val g = Graph {
          import graph._

          val v = Metro(n, phase).take(sampleLen)
          DebugIntPromise(v, p)
        }

        runGraph(g, 1024)

        assert(p.isCompleted)
        val res: Try[Vec[Int]] = p.future.value.get
        val exp = if (n == 0) {
          Vector.tabulate(sampleLen)(i => if ((i + phase) == 0) 1 else 0)
        } else {
          Vector.tabulate(sampleLen)(i => if (((i + phase) % n) == 0) 1 else 0)
        }
        assert (res === Success(exp), s"$res does not match $n")
      }
    }
  }

  it should "work with sequential period values" in {
    val p = Promise[Vec[Int]]()
    val periods   = List(2, 7, 13) // 1010000001000000000000
    val sampleLen = periods.sum

    val g = Graph {
      import graph._

      val periodsG = periods.map(ConstantI(_): GE).reduce(_ ++ _)
//      periodsG.poll(1, "all")
      val v = Metro(periodsG).take(sampleLen)
      DebugIntPromise(v, p)
    }

    runGraph(g, 512)

    assert(p.isCompleted)
    val res: Try[Vec[Int]] = p.future.value.get
    val exp = periods.flatMap(p => Vector.tabulate(p)(i => if (i == 0) 1 else 0))
    // println(exp.mkString)
    assert (res === Success(exp), s"$res does not match $exp")
  }
}