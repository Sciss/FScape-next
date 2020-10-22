package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class ZipWindowNSpec extends UGenSpec {
  "The ZipWindowN UGen" should "work as specified" in {
    for {
      winLen  <- Seq(1, 4, 15, 16, 17)
      seqLen  <- Seq(0, 4, 15, 16, 17)
      numCh   <- Seq(1, 2, 3)
    } {
      val p = Promise[Vec[Int]]()

      val g = Graph {
        import graph._

        val in: GE = Vector.tabulate(numCh) { ch =>
          ArithmSeq(ch * 100, length = seqLen)
        }
        val z = ZipWindowN(in, winLen)
        DebugIntPromise(z, p)
      }

      runGraph(g, 16)

      assert(p.isCompleted)
      val res   = getPromiseVec(p)
      val inSq  = Vector.tabulate(numCh) { ch =>
        (ch * 100) until (ch * 100 + seqLen)
      }
      val numW = (seqLen + winLen - 1) / winLen
      val exp  = (0 until numW).flatMap { wi =>
        inSq.flatMap(_.slice(wi * winLen, (wi + 1) * winLen).padTo(winLen, 0))
      }
      assert (res === exp, s"; for winLen $winLen, seqLen $seqLen, numCh $numCh")
    }
  }
}