package de.sciss.fscape

import de.sciss.fscape.graph.UnaryOp
import de.sciss.fscape.graph.UnaryOp.Op
import de.sciss.kollflitz.Vec

import scala.concurrent.Promise

class UnaryOpSpec extends UGenSpec {
  def isD(xs: Vec[Any]): Boolean = xs.forall(_.isInstanceOf[Double ])
  def isI(xs: Vec[Any]): Boolean = xs.forall(_.isInstanceOf[Int    ])
  def isL(xs: Vec[Any]): Boolean = xs.forall(_.isInstanceOf[Long   ])

  def asD(xs: Vec[Any]): Vec[Double ] = xs.asInstanceOf[Vec[Double ]]
  def asI(xs: Vec[Any]): Vec[Int    ] = xs.asInstanceOf[Vec[Int    ]]
  def asL(xs: Vec[Any]): Vec[Long   ] = xs.asInstanceOf[Vec[Long   ]]

  "The UnaryOp UGen" should "work as intended" in {
    val dataSq = Seq[(Op, Vec[Any], Vec[Any])](
      (UnaryOp.Abs        , Vec(-3.0, -0.3, -0.0, 0.0, 1.3), Vec(3.0, 0.3, 0.0, 0.0, 1.3)),   // DD
      (UnaryOp.Abs        , Vec(-3, 0, 1)                  , Vec(3, 0, 1)),                   // II
      (UnaryOp.Abs        , Vec(-3L, 0L, 1L)               , Vec(3L, 0L, 1L)),                // LL
      (UnaryOp.IsNaN      , Vec(-3.0, NaN, 0.0, Double.PositiveInfinity) , Vec(0, 1, 0, 0)),  // DI
      (UnaryOp.ToLong     , Vec(-3.0, -0.3, 0.0, Double.PositiveInfinity), Vec(-3L, 0L, 0L, Long.MaxValue)),  // DL
      (UnaryOp.Reciprocal , Vec(-3, 4, 1), Vec(-1.0/3, 1.0/4, 1.0)),                    // ID
      (UnaryOp.Squared    , Vec(-3, 4, 1), Vec(9L, 16L, 1L)),                           // IL
      (UnaryOp.ToInt      , Vec(-3L, 4L, Long.MaxValue), Vec(-3, 4, -1)),               // LI
      (UnaryOp.Reciprocal , Vec(-3L, 4L, Long.MaxValue), Vec(-1.0/3, 1.0/4, 1.0/Long.MaxValue)),  // LD
    )

    for {
      (op, dataIn, exp) <- dataSq
    } {
      import graph._
      val p = Promise[Vec[Any]]()

      val g = Graph {
        val in: GE = if (isD(dataIn)) {
          val inSq = asD(dataIn)
          ValueDoubleSeq(inSq: _*)
        } else if (isI(dataIn)) {
          val inSq = asI(dataIn)
          ValueIntSeq(inSq: _*)
        } else {
          assert(isL(dataIn))
          val inSq = asL(dataIn)
          ValueLongSeq(inSq: _*)
        }
        val sig = UnaryOp(op.id, in)
        DebugAnyPromise(sig, p)
      }

      runGraph(g)
      val obs = p.future.value.get.get
      assert (exp === obs, s"For $op")
    }
  }
}