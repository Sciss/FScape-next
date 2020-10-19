package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class UnzipWindowNSpec extends UGenSpec {
  "The UnzipWindowN UGen" should "work as specified" in {
    for {
      winLen  <- Seq(1, 4, 15, 16, 17)
      seqLen  <- Seq(0, 4, 15, 16, 17)
      numCh   <- Seq(1, 2, 3)
    } {
      val ps = Vec.fill(numCh)(Promise[Vec[Int]]())

      val numW  = (seqLen + winLen - 1) / winLen
      val inSq  = Vector.tabulate(numCh) { ch =>
        val t = ((ch * 100) until (ch * 100 + seqLen)).toVector
        t.padTo(numW * winLen, 0)
      }
      val inZip = (0 until numW).flatMap { wi =>
        inSq.zipWithIndex.flatMap { case (sq, ch) =>
          val sl = sq.slice(wi * winLen, (wi + 1) * winLen)
          sl
          // if (wi == numW - 1 && ch == numCh - 1) sl else sl.padTo(winLen, 0)
        }
      }

      val g = Graph {
        import graph._

        // avoid using ZipWindowN so we can debug better
//        val in0: GE = Vector.tabulate(numCh) { ch =>
//          ArithmSeq(ch * 100, length = seqLen)
//        }
//        val in = ZipWindowN(in0, winLen)
        val in = ValueIntSeq(inZip: _*)

        val z0 = UnzipWindowN(numCh, in, winLen)
        for (ch <- 0 until numCh) {
          val z = z0.out(ch)
          // z.poll(1, s"ch-$ch")
          // Length(z).poll(s"len-$ch")
          DebugIntPromise(z, ps(ch))
        }
      }

      val info = s"winLen $winLen, seqLen $seqLen, numCh $numCh"
      // println(info)
      runGraph(g, 16)

      val resSq = ps.map(getPromiseVec)
      assert (resSq.size === inSq.size)
      (resSq zip inSq).zipWithIndex.foreach { case ((res, exp), ch) =>
        assert (res === exp, s"; for $info, ch = $ch")
      }
    }
  }

  it should "work when some outputs are unused" in {
    for {
      winLen  <- Seq(1, 4, 15, 16, 17)
      seqLen  <- Seq(0, 4, 15, 16, 17)
      numCh   <- Seq(2, 3)
      useCh   <- 1 until numCh
    } {
      val ps = Vec.fill(numCh)(Promise[Vec[Int]]())

      val numW  = (seqLen + winLen - 1) / winLen
      val inSq  = Vector.tabulate(numCh) { ch =>
        val t = ((ch * 100) until (ch * 100 + seqLen)).toVector
        t.padTo(numW * winLen, 0)
      }
      val inZip = (0 until numW).flatMap { wi =>
        inSq.flatMap { sq =>
          sq.slice(wi * winLen, (wi + 1) * winLen)
        }
      }

      val g = Graph {
        import graph._
        val in = ValueIntSeq(inZip: _*)
        val z0 = UnzipWindowN(numCh, in, winLen)
        for (ch <- 0 until useCh) {
          val z = z0.out(ch)
          DebugIntPromise(z, ps(ch))
        }
      }

      val info = s"winLen $winLen, seqLen $seqLen, numCh $numCh, useCh $useCh"
      // println(info)
      runGraph(g, 16)

      val resSq = ps.take(useCh).map(getPromiseVec)
      assert (resSq.size === useCh)
      (resSq zip inSq).zipWithIndex.foreach { case ((res, exp), ch) =>
        assert (res === exp, s"; for $info, ch = $ch")
      }
    }
  }
}