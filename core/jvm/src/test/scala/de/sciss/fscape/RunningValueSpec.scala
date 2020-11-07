package de.sciss.fscape

import de.sciss.fscape.Ops._
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class RunningValueSpec extends UGenSpec {
  "The RunningMin/Max/Sum/Product UGens" should "work as specified" in {
    def run(n: Int, block: Int)(sqPrep: GE => GE): (Vec[Any], Vec[Any], Vec[Any], Vec[Any]) = {
      val pMin = Promise[Vec[Any]]()
      val pMax = Promise[Vec[Any]]()
      val pSum = Promise[Vec[Any]]()
      val pPrd = Promise[Vec[Any]]()

      val g = Graph {
        import graph._

        val x    = sqPrep(ArithmSeq(1, length = n)) // .toDouble
        val rMin = RunningMin     (x)
        val rMax = RunningMax     (x)
        val rSum = RunningSum     (x)
        val rPrd = RunningProduct (x)
        DebugAnyPromise(rMin, pMin)
        DebugAnyPromise(rMax, pMax)
        DebugAnyPromise(rSum, pSum)
        DebugAnyPromise(rPrd, pPrd)
      }

      runGraph(g, block)

      val resMin: Vec[Any] = getPromiseVec(pMin)
      val resMax: Vec[Any] = getPromiseVec(pMax)
      val resSum: Vec[Any] = getPromiseVec(pSum)
      val resPrd: Vec[Any] = getPromiseVec(pPrd)

      (resMin, resMax, resSum, resPrd)
    }

    for {
      n <- List(0, 1, 10, 127, 128, 129)
    } {
      val (resMin, resMax, resSum, resPrd) = run(n, 128)(_.toDouble)
      val seq   = (1 to n).map(_.toDouble)

      import Double.{PositiveInfinity => inf}
      val expMin = seq.scanLeft(+inf)(_ min _).tail
      val expMax = seq.scanLeft(-inf)(_ max _).tail
      val expSum = seq.scanLeft(0.0 )(_ + _)  .tail
      val expPrd = seq.scanLeft(1.0 )(_ * _)  .tail
      assert (resMin === expMin, s"n $n - Double")
      assert (resMax === expMax, s"n $n - Double")
      assert (resSum === expSum, s"n $n - Double")
      assert (resPrd === expPrd, s"n $n - Double")
    }

    for {
      n <- List(0, 1, 15, 16, 17)
    } {
      val (resMin, resMax, resSum, resPrd) = run(n, 16)(identity)
      val seq = 1 to n

      val expMin = seq.scanLeft(Int.MaxValue)(_ min _).tail
      val expMax = seq.scanLeft(Int.MinValue)(_ max _).tail
      val expSum = seq.scanLeft(0)(_ + _)  .tail
      val expPrd = seq.scanLeft(1)(_ * _)  .tail
      assert (resMin === expMin, s"n $n - Int")
      assert (resMax === expMax, s"n $n - Int")
      assert (resSum === expSum, s"n $n - Int")
      assert (resPrd === expPrd, s"n $n - Int")
    }
  }
}