/*
 *  IfElse.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.graph

import de.sciss.fscape
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{BufD, BufI, BufL, OutD, OutI, OutL, StreamIn, StreamOut}
import de.sciss.fscape.{GE, Graph, Lazy, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}

/** Beginning of a conditional block.
  *
  * @param cond   the condition, which is treated as a boolean with zero being treated as `false` and
  *               non-zero as `true`. Note that multi-channel expansion does not apply here, because
  *               the branches may have side-effects which are not supposed to be repeated across
  *               channels. Instead, if `cond` expands to multiple channels, they are combined using
  *               logical `|` (OR).
  *
  * @see  [[IfThen]]
  */
final case class If(cond: GE) {
  def Then [A](branch: => A): IfThen[A] = {
    var res: A = null.asInstanceOf[A]
    val g = Graph {
      res = branch
    }
    IfThen(cond, g, res)
  }
}

object Then {
  private[fscape] case class UnitCase (cond: GE, branchLayer: Int)
  private[fscape] case class GECase   (cond: UGenIn, branchLayer: Int, branchOut: Vec[UGenIn])

  private[fscape] def gatherUnit[A](e: Then[Any])(implicit b: UGenGraph.Builder): List[UnitCase] = {
    @tailrec
    def loop(t: Then[Any], res: List[UnitCase]): List[UnitCase] = {
      val layer = b.expandNested(t.branch)
      val res1  = UnitCase(t.cond, layer) :: res
      t match {
        case hd: ElseOrElseIfThen[Any] => loop(hd.pred, res1)
        case _ => res1
      }
    }

    loop(e, Nil)
  }

  private[fscape] def gatherGE[A](e: Then[GE])(implicit builder: UGenGraph.Builder): List[GECase] = {
    @tailrec
    def loop(t: Then[GE], res: List[GECase]): List[GECase] = {
      val layer       = builder.expandNested(t.branch)
      val condE       = t.cond    .expand
      val branchOutE  = t.result  .expand
      val condI       = condE.unbubble match {
        case u: UGenIn => u
        case g: UGenInGroup =>
          import de.sciss.fscape.Ops._
          g.flatOutputs.foldLeft(0: UGenIn)((a, b) => (a | b).expand.flatOutputs.head)
      }
      val branchOutI  = branchOutE.flatOutputs  // XXX TODO --- what else could we do?
      val res1        = GECase(condI, layer, branchOutI) :: res
      t match {
        case hd: ElseOrElseIfThen[GE] => loop(hd.pred, res1)
        case _ => res1
      }
    }

    loop(e, Nil)
  }

  case class SourceUnit(cases: List[UnitCase]) extends UGenSource.ZeroOut {
    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, cases.iterator.map(_.cond.expand).toIndexedSeq)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, args, adjuncts = cases.map(c => Adjunct.Int(c.branchLayer)))
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val cond = (args.iterator zip cases.iterator).map { case (cond0, UnitCase(_, bl)) =>
        (cond0.toInt, bl)
      } .toVector
      stream.IfThenUnit(cond)
    }
  }

  case class SourceGE(cases: List[GECase]) extends UGenSource.MultiOut {
    private[this] val numCases = cases.size

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val branchSizes = cases.map(_.branchOut.size)
      val isEmpty     = branchSizes.isEmpty || branchSizes.contains(0)
      val numOutputs  = if (isEmpty) 0 else branchSizes.max
      val args = cases.iterator.flatMap {
        case GECase(cond, _, branchOut) =>
          val branchOutExp = Vector.tabulate(numOutputs) { ch =>
            branchOut(ch % branchOut.size)
          }
          cond +: branchOutExp
      } .toIndexedSeq
      makeUGen(args)
    }

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
      val numOutputs = args.size / numCases - 1 // one conditional, N branch-outs
      UGen.MultiOut(this, args, numOutputs = numOutputs, adjuncts = cases.map(c => Adjunct.Int(c.branchLayer)))
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] = {
      val sliceSize   = args.size / numCases
      val numOutputs  = sliceSize - 1 // one conditional, N branch-outs
      val condT       = Vector.tabulate(numCases)(bi => args(bi * sliceSize).toInt)
      val outs        = Vector.tabulate(numCases)(bi => args.slice(bi * sliceSize + 1, (bi + 1) * sliceSize))
      val outsF       = outs.flatten
      val layers      = cases .map(_.branchLayer)

      if (outsF.forall(_.isInt)) {
        val outsT: Seq[Vec[OutI]] = outs.map(_.map(_.toInt))
        val cases = (condT, layers, outsT).zipped.toList
        stream.IfThenGE[Int, BufI](numOutputs, cases)

      } else if (outsF.forall(o => o.isInt || o.isLong)) {
        val outsT: Seq[Vec[OutL]] = outs.map(_.map(_.toLong))
        val cases = (condT, layers, outsT).zipped.toList
        stream.IfThenGE[Long, BufL](numOutputs, cases)

      } else {
        val outsT: Seq[Vec[OutD]] = outs.map(_.map(_.toDouble))
        val cases = (condT, layers, outsT).zipped.toList
        stream.IfThenGE[Double, BufD](numOutputs, cases)
      }
    }
  }
}
sealed trait Then[+A] extends Lazy {
  def cond  : GE
  def branch: Graph
  def result: A
}

sealed trait IfOrElseIfThen[+A] extends Then[A] {
  import fscape.graph.{Else => _Else}
  def Else [B >: A, Res](branch: => B)(implicit result: _Else.Result[B, Res]): Res = {
    Graph.builder.removeLazy(this)
    result.make(this, branch)
  }
}

sealed trait IfThenLike[+A] extends IfOrElseIfThen[A] with Lazy.Expander[Unit] {
  final def ElseIf (cond: GE): ElseIf[A] = {
    Graph.builder.removeLazy(this)
    new ElseIf(this, cond)
  }

  final protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val cases = Then.gatherUnit(this)
    // println(s"cases = $cases")
    Then.SourceUnit(cases)
    ()
  }
}

/** A side effecting conditional block. To turn it into a full `if-then-else` construction,
  * call `Else` or `ElseIf`.
  *
  * @see  [[Else]]
  * @see  [[ElseIf]]
  */
final case class IfThen[+A](cond: GE, branch: Graph, result: A) extends IfThenLike[A]

final case class ElseIf[+A](pred: IfOrElseIfThen[A], cond: GE) {
  def Then [B >: A](branch: => B): ElseIfThen[B] = {
    var res: B = null.asInstanceOf[B]
    val g = Graph {
      res = branch
    }
    ElseIfThen[B](pred, cond, g, res)
  }
}

sealed trait ElseOrElseIfThen[+A] extends Then[A] {
  def pred: IfOrElseIfThen[A]
}

final case class ElseIfThen[+A](pred: IfOrElseIfThen[A], cond: GE, branch: Graph, result: A)
  extends IfThenLike[A] with ElseOrElseIfThen[A]

object Else {
  object Result extends LowPri {
    implicit def GE: Else.GE.type = Else.GE
  }
  sealed trait Result[-A, Res] {
    def make(pred: IfOrElseIfThen[A], branch: => A): Res
  }

  object GE extends Result[fscape.GE, ElseGE] {
    def make(pred: IfOrElseIfThen[GE], branch: => GE): ElseGE = {
      var res: GE = null
      val g = Graph {
        res = branch
      }
      ElseGE(pred, g, res)
    }
  }

  final class Unit[A] extends Result[A, ElseUnit] {
    def make(pred: IfOrElseIfThen[A], branch: => A): ElseUnit =  {
      val g = Graph {
        branch
      }
      ElseUnit(pred, g)
    }
  }

  trait LowPri {
    implicit final def Unit[A]: Unit[A] = instance.asInstanceOf[Unit[A]]
    private final val instance = new Unit[Any]
  }
}

sealed trait ElseLike[+A] extends ElseOrElseIfThen[A] {
  def cond: GE = ConstantI.C1
}

final case class ElseUnit(pred: IfOrElseIfThen[Any], branch: Graph)
  extends ElseLike[Any] with Lazy.Expander[Unit] {

  def result: Any = ()

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val cases = Then.gatherUnit(this)
    // println(s"cases = $cases")
    Then.SourceUnit(cases)
    ()
  }
}

final case class ElseGE(pred: IfOrElseIfThen[GE], branch: Graph, result: GE)
  extends ElseLike[GE] with GE.Lazy {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val cases = Then.gatherGE(this)
    // println(s"cases = $cases")
    Then.SourceGE(cases)
  }
}