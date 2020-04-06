package de.sciss.fscape

import de.sciss.fscape.graph.Constant
import de.sciss.fscape.stream.Control.Config
import de.sciss.kollflitz.Vec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

class UGenSpec extends AnyFlatSpec with Matchers {
  final val Pi  : Double = math.Pi
  final val Pi2 : Double = 2 * Pi
  final val PiH : Double = 0.5 * Pi
  final val NaN : Double = java.lang.Double.NaN

  val eps: Double = 1.0e-5

  def isD(xs: Vec[Any]): Boolean = xs.forall(_.isInstanceOf[Double ])
  def isI(xs: Vec[Any]): Boolean = xs.forall(_.isInstanceOf[Int    ])
  def isL(xs: Vec[Any]): Boolean = xs.forall(_.isInstanceOf[Long   ])

  def asD(xs: Vec[Any]): Vec[Double ] = xs.asInstanceOf[Vec[Double ]]
  def asI(xs: Vec[Any]): Vec[Int    ] = xs.asInstanceOf[Vec[Int    ]]
  def asL(xs: Vec[Any]): Vec[Long   ] = xs.asInstanceOf[Vec[Long   ]]

  def asGE[A](in: Seq[A])(implicit view: A => Constant): GE =
    if (in.size == 1) in.head: GE else in.map(view(_): GE).reduce(_ ++ _)

  def difOk(obs: Vec[Double], exp: Vec[Double], info: String = ""): Unit = {
    assert (obs.size === exp.size, s"; $info")
    (obs zip exp).zipWithIndex.foreach { case ((obsV, expV), idx) =>
      assert (obsV === expV +- eps, s"For idx $idx of ${obs.size}; $info")
    }
  }

  def difRadiansOk(obs: Vec[Double], exp: Vec[Double]): Unit = {
    assert (obs.size === exp.size)
    (obs zip exp).zipWithIndex.foreach { case ((obsV, expV), idx) =>
      val a = (expV - obsV + Pi) % Pi2 - Pi
      // https://stackoverflow.com/questions/1878907/the-smallest-difference-between-2-angles
      assert (a < eps, s"For idx $idx of ${obs.size}")
    }
  }

  def runGraph(g: Graph, blockSize: Int = 1024): Unit = {
    val cfg = Config()
    cfg.blockSize = blockSize
    val ctl = stream.Control(cfg)
    ctl.run(g)
    Await.result(ctl.status, Duration.Inf)
  }

  def getPromiseVec[A](in: Promise[Vec[A]]): Vec[A] =
    in.future.value.get.get

  def getPromise[A](in: Promise[Vec[A]]): A = {
    val sq = getPromiseVec(in)
    assert (sq.size === 1)
    sq.head
  }
}
