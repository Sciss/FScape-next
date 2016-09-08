/*
 *  Attribute.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenGraph.Builder

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

object Attribute {
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
  }
  final case class Scalar(const: Constant) extends Default {
    def numChannels = 1

    def tabulate(n: Int)(implicit b: UGenGraph.Builder): UGenInLike = {
      val exp = const.expand
      if (n == 1) exp else UGenInGroup(scala.Vector.fill(n)(exp))
    }

    // serialization!
    override def productPrefix: String = "Attribute$Scalar"
  }

  final case class Vector(values: Vec[Constant]) extends Default {
    def numChannels: Int = values.size

    def tabulate(n: Int)(implicit b: UGenGraph.Builder): UGenInLike = {
      val exp   = values.map(_.expand)
      val sz    = exp.size
      val wrap  = if (n == sz) exp else scala.Vector.tabulate(n)(idx => exp(idx % sz))
      UGenInGroup(wrap)
    }

    // serialization!
    override def productPrefix: String = "Attribute$Vector"
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
}
final case class Attribute(key: String, default: Option[Attribute.Default], fixed: Int)
  extends GE.Lazy {

  protected def makeUGens(implicit b: Builder): UGenInLike = {
    ???
//    val b         = UGenGraphBuilder.get
//    val defChans  = default.fold(-1)(_.size)
//    val inValue   = b.requestInput(Input.Scalar(
//      name                = key,
//      requiredNumChannels = fixed,
//      defaultNumChannels  = defChans))
//    val numCh   = inValue.numChannels
//    val ctlName = Attribute.controlName(key)
//    val values  = default.fold(Vector.fill(numCh)(0f))(df => Vector.tabulate(numCh)(idx => df(idx % defChans)))
//    val nameOpt = Some(ctlName)
//    val ctl     = if (rate == audio)
//      AudioControlProxy(values, nameOpt)
//    else
//      ControlProxy(rate, values, nameOpt)
//    ctl.expand
  }
}