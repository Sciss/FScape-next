package de.sciss.fscape

import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

class RunningValueSpec extends AnyFlatSpec with Matchers {
  "The RunningMin/Max/Sum/Product UGens" should "work as specified" in {
    val lengths = List(
      0, 1, 10, 100, 1024, 1025
    )

    lengths.foreach { n =>
      val pMin = Promise[Vec[Double]]()
      val pMax = Promise[Vec[Double]]()
      val pSum = Promise[Vec[Double]]()
      val pPrd = Promise[Vec[Double]]()

      val g = Graph {
        import graph._

        val x    = ArithmSeq(1, length = n)
        val rMin = RunningMin     (x)
        val rMax = RunningMax     (x)
        val rSum = RunningSum     (x)
        val rPrd = RunningProduct (x)
        DebugDoublePromise(rMin, pMin)
        DebugDoublePromise(rMax, pMax)
        DebugDoublePromise(rSum, pSum)
        DebugDoublePromise(rPrd, pPrd)
      }

      val ctl = stream.Control()
      ctl.run(g)
      Await.result(ctl.status, Duration.Inf)

      def get(p: Promise[Vec[Double]]): Vec[Double] = p.future.value.get.get

      val resMin: Vec[Double] = get(pMin)
      val resMax: Vec[Double] = get(pMax)
      val resSum: Vec[Double] = get(pSum)
      val resPrd: Vec[Double] = get(pPrd)
      val seq   = (1 to n).map(_.toDouble)

      import Double.{PositiveInfinity => inf}
      val expMin = seq.scanLeft(+inf)(_ min _).tail
      val expMax = seq.scanLeft(-inf)(_ max _).tail
      val expSum = seq.scanLeft(0.0)(_ + _).tail
      val expPrd = seq.scanLeft(1.0)(_ * _).tail
      assert (resMin === expMin)
      assert (resMax === expMax)
      assert (resSum === expSum)
      assert (resPrd === expPrd)
    }
  }
}