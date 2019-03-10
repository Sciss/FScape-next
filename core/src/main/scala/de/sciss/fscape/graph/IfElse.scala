/*
 *  IfElse.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.graph

import de.sciss.fscape
import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{Builder, StreamIn, StreamOut}
import de.sciss.fscape.{GE, Graph, Lazy, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

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
  private[fscape] case class Case(cond: GE, branchLayer: Int)

  private[fscape] def gather[A](e: Then[Any])(implicit b: UGenGraph.Builder): List[Case] = {
    def loop(t: Then[Any], res: List[Case]): List[Case] = {
      val layer = b.expandNested(t.branch)
      val res1  = Case(t.cond, layer) :: res
      e match {
        case hd: ElseOrElseIfThen[Any] => loop(hd, res1)
        case _ => res1
      }
    }

    loop(e, Nil)
  }

  case class SourceUnit(cases: List[Case]) extends UGenSource.ZeroOut {
    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, cases.iterator.map(_.cond.expand).toIndexedSeq)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, aux = cases.map(c => Aux.Int(c.branchLayer)))

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val cond = (args.iterator zip cases.iterator).map { case (cond0, Case(_, bl)) =>
        (cond0.toInt, bl)
      } .toVector
      stream.IfThenUnit(cond)
    }
  }
}
sealed trait Then[+A] extends Lazy {
  def cond  : GE
  def branch: Graph
  def result: A

//  private[synth] final def force(b: UGenGraph.Builder): Unit = UGenGraph.builder match {
//    case nb: NestedUGenGraphBuilder => visit(nb)
//    case _ => sys.error(s"Cannot expand modular IfGE outside of NestedUGenGraphBuilder")
//  }

//  private[fscape] def force(b: UGenGraph.Builder): Unit = visit(b)
//
//  private[fscape] final def visit(b: UGenGraph.Builder): UGenGraph.ExpIfCase =
//    b.visit(ref, b.expandIfCase(this))
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
    val layer = b.expandNested(branch)
    Then.SourceUnit(Then.Case(cond, layer) :: Nil)
  }
}

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
  extends IfThenLike[A]

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
    val cases = Then.gather(this)
    println(s"cases = $cases")
    Then.SourceUnit(cases)
  }
}

object ElseGE {
  case class WithRef(cond: GE, branchLayer: Int) extends UGenSource.SingleOut {
    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      unwrap(this, Vector(cond.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
      UGen.SingleOut(this, args, aux = Aux.Int(branchLayer) :: Nil)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: Builder): StreamOut = {
      val Vec(cond) = args
      // println(s"layer = $layer")
      // stream.IfThen(cond = cond.toInt, branchLayer = branchLayer)
      ???
    }
  }
}
final case class ElseGE(pred: IfOrElseIfThen[GE], branch: Graph, result: GE)
  extends ElseLike[GE] with GE.Lazy {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val layer = b.expandNested(branch)
    pred match {
      case IfThen     (condP, branchP, resP)        =>
      case ElseIfThen (predP, condP, branchP, resP) =>
    }
    ElseGE.WithRef(cond, layer)
  }
}