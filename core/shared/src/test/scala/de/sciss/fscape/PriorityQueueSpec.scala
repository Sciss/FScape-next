package de.sciss.fscape

import de.sciss.kollflitz.Vec

import scala.collection.mutable
import scala.concurrent.Promise

class PriorityQueueSpec extends UGenSpec {
  "The PriorityQueue UGen" should "work as intended" in {
    val r = new util.Random(4L)
    for {
      keyLen   <- Seq(0, 1, 10, 63, 64, 65)
      valueLen <- Seq(keyLen - 1, keyLen, keyLen + 1)
      qLen     <- Seq(0, 1, 10, 127, 128, 129)
    } {
      val keySq = Vector.fill(keyLen)(r.nextInt(keyLen)/*.toDouble*/) // allow duplicate keys
//      println(keySq.mkString(", "))
      val p = Promise[Vec[Int]]()
      val g = Graph {
        import graph._
//        val keys    = ValueDoubleSeq(keySq: _*)
        val keys    = ValueIntSeq(keySq: _*)
        val values  = ArithmSeq(start = 1, length = valueLen)
        val pq      = PriorityQueue(keys, values, size = qLen)
        DebugIntPromise(pq, p)
      }

      val info = s"keyLen $keyLen, valueLen $valueLen, qLen $qLen"
//      println(info)
      runGraph(g, 64)

      assert(p.isCompleted)
      val obs       = getPromiseVec(p)
      val valueSq   = Vector.tabulate(valueLen)(_ + 1)
      val (xs, ys)  = (keySq zip valueSq).splitAt(qLen)
      val q         = mutable.PriorityQueue[(Int /*Double*/, Int)](xs: _*)(Ordering.by(-_._1))  // highest priority = lowest key
      for {
        y <- ys
        if q.nonEmpty && y._1 > q.head._1
      } {
        q.dequeue()
        q += y
      }
      // note: Queue may return different value orderings if there are duplicate keys
      // probably because of hash codes
      val expKV = q.dequeueAll/*[(Int /*Double*/, Int)]*/.reverse.toVector
      // assert (res === exp, info)

//      @tailrec
//      def group(off: Int, res: (Vec[Vec[Int]], Vec[Vec[Int]])): (Vec[Vec[Int]], Vec[Vec[Int]]) =
//        if (off == expKV.size) res else {
//          val key   = expKV(off)._1
//          val off1  = expKV.segmentLength(_._1 == key, off) + off
//          val vObs0 = res._1
//          val vExp0 = res._2
//          val vExp: Vec[Int] = expKV .slice(off, off1).map(_._2).sorted
//          val vObs: Vec[Int] = obs   .slice(off, off1)          .sorted
//          val res1 = (vObs0 :+ vObs, vExp0 :+ vExp)
//          group(off = off1, res = res1)
//        }
//
//      val (obsFix, expFix) = group(off = 0, res = (Vector.empty, Vector.empty))

      // because multiple keys can be identical, but lead to different values,
      // we actually need to compare the keys instead of the values.
      val obsFix = obs.map { v =>
        val i = valueSq.indexOf(v)
        keySq(i)
      }

      val expFix = expKV.map(_._1)

//      println(obsFix/*.flatten*/.mkString(", "))
//      println(expFix/*.flatten*/.mkString(", "))
      assert (obsFix === expFix, info)
    }
  }
}