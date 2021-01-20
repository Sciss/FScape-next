/*
 *  Attribute.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.lucre.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenGraph.Builder
import de.sciss.fscape.graph.{Constant, ConstantD, ConstantI, ConstantL, UGenInGroup}
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.fscape.{GE, UGenGraph, UGenInLike}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object Attribute extends ProductReader[Attribute] {
  final class Factory(val `this`: String) extends AnyVal { me =>
    import me.{`this` => name}

    /** Creates an attribute without defaults (attribute must be present). */
    def attr: Attribute = Attribute(key = name)
    /** Creates an attribute with defaults (attribute may be absent). */
    def attr(values: Default): Attribute = Attribute(key = name, default = values)
  }

  object Default {
    implicit def fromInt    (in:     Int    ): Default = Scalar(in)
    implicit def fromDouble (in:     Double ): Default = Scalar(in)
    implicit def fromLong   (in:     Long   ): Default = Scalar(in)
    implicit def fromDoubles(in: Vec[Double]): Default = Vector(in.map(x => ConstantD(x)))
  }
  /** Magnet pattern */
  sealed trait Default extends Product {
    def numChannels: Int
    // def tabulate(n: Int): Vec[Float]
    def tabulate(n: Int)(implicit b: UGenGraph.Builder): UGenInLike

    def expand: UGenInLike
  }

  object Scalar extends ProductReader[Scalar] {
    override def read(in: RefMapIn, key: String, arity: Int): Scalar = {
      require (arity == 1)
      val _const = in.readProductT[Constant]()
      new Scalar(_const)
    }
  }
  final case class Scalar(const: Constant) extends Default {
    def numChannels = 1

    def expand: UGenInLike = const

    def tabulate(n: Int)(implicit b: UGenGraph.Builder): UGenInLike = {
      val exp = const.expand
      if (n == 1) exp else UGenInGroup(scala.Vector.fill(n)(exp))
    }

    // serialization!
    override def productPrefix: String = s"Attribute$$Scalar"
  }

  object Vector extends ProductReader[Vector] {
    override def read(in: RefMapIn, key: String, arity: Int): Vector = {
      require (arity == 1)
      val _values = in.readVec(in.readProductT[Constant]())
      new Vector(_values)
    }
  }
  final case class Vector(values: Vec[Constant]) extends Default {
    def numChannels: Int = values.size

    def expand: UGenInLike = UGenInGroup(values)

    def tabulate(n: Int)(implicit b: UGenGraph.Builder): UGenInLike = {
      val exp   = values.map(_.expand)
      val sz    = exp.size
      val wrap  = if (n == sz) exp else scala.Vector.tabulate(n)(idx => exp(idx % sz))
      UGenInGroup(wrap)
    }

    // serialization!
    override def productPrefix: String = s"Attribute$$Vector"
  }

  def apply(key: String): Attribute =
    apply(key, None, fixed = -1)

  def apply(key: String, fixed: Int): Attribute =
    apply(key, None, fixed = fixed)

  def apply(key: String, default: Default): Attribute =
    mk(key, default, fixed = false)

  def apply(key: String, default: Default, fixed: Boolean): Attribute =
    mk(key, default, fixed = fixed)

  private def mk(key: String, default: Default, fixed: Boolean): Attribute =
    new Attribute(key, Some(default), fixed = if (fixed) default.numChannels else -1)

  override def read(in: RefMapIn, key: String, arity: Int): Attribute = {
    require (arity == 3)
    val _key      = in.readString()
    val _default  = in.readOption(in.readProductT[Attribute.Default]())
    val _fixed    = in.readInt()
    new Attribute(_key, _default, _fixed)
  }
}
final case class Attribute(key: String, default: Option[Attribute.Default], fixed: Int)
  extends GE.Lazy {

  protected def makeUGens(implicit b: Builder): UGenInLike = {
    val ub = UGenGraphBuilder.get(b)
    // val defChans  = default.fold(-1)(_.size)
    val res: UGenInLike = ub.requestInput(Input.Attribute(key)).peer.fold[UGenInLike] {
      val d = default.getOrElse(sys.error(s"Missing Attribute $key"))
      if (fixed < 0) d.expand else d.tabulate(fixed)
    } { value =>
      val res0: UGenInLike = value match {
        case d: Double  => ConstantD(d)
        case i: Int     => ConstantI(i)
        case n: Long    => ConstantL(n)
        case b: Boolean => ConstantI(if (b) 1 else 0)
        case sq: Vec[_] if sq.forall(_.isInstanceOf[Double]) =>
          val sqT = sq.asInstanceOf[Vec[Double]]
          UGenInGroup(sqT.map(ConstantD(_)))
        case sq: Vec[_] if sq.forall(_.isInstanceOf[Int]) =>
          val sqT = sq.asInstanceOf[Vec[Int]]
          UGenInGroup(sqT.map(ConstantI(_)))
        case other      => sys.error(s"Cannot use value $other as Attribute UGen $key")
      }
      val sz = res0.outputs.size
      if (fixed < 0 || fixed == sz) res0 else
        UGenInGroup(Vector.tabulate(fixed)(idx => res0.outputs(idx % sz)))
    }
    res
  }
}