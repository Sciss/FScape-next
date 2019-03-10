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
import de.sciss.fscape.stream.{Builder, StreamIn}
import de.sciss.fscape.{GE, Graph, Lazy, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}
import de.sciss.synth.io.AudioFileSpec

import scala.collection.immutable
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

sealed trait IfThenLike[+A] extends IfOrElseIfThen[A] {
  def ElseIf (cond: GE): ElseIf[A] = {
    Graph.builder.removeLazy(this)
    new ElseIf(this, cond)
  }
}

object IfThen {
  case class WithRef(cond: GE, branchLayer: Int) extends UGenSource.ZeroOut {
    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, Vector(cond.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
      UGen.ZeroOut(this, args, aux = Aux.Int(branchLayer) :: Nil)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val Vec(cond) = args
      // println(s"layer = $layer")
      stream.IfThen(cond = cond.toInt, branchLayer = branchLayer)
    }
  }
}

final case class IfThen[+A](cond: GE, branch: Graph, result: A)
  extends IfThenLike[A] with Lazy.Expander[Unit] {


//  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
//    unwrap(this, Vector(cond.expand))
//
//  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
//    UGen.ZeroOut(this, inputs = args, aux = Aux.String(label) :: Nil)
//
//  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
//    val Vec(cond) = args
//
//  }

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val layer = b.expandNested(branch)
    IfThen.WithRef(cond, layer)
  }
}

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
  extends IfOrElseIfThen[A] with ElseOrElseIfThen[A] {

  def ElseIf (cond: GE): ElseIf[A] = new ElseIf(this, cond)

  private[fscape] def force(b: UGenGraph.Builder): Unit = ???
}

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
  extends ElseLike[Any] {

  def result: Any = ()

  private[fscape] def force(b: UGenGraph.Builder): Unit = ???
}

final case class ElseGE(pred: IfOrElseIfThen[GE], branch: Graph, result: GE)
  extends ElseLike[GE] with GE.Lazy {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = ???
}