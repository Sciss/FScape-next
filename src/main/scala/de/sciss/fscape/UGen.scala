/*
 *  UGen.scala
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

import de.sciss.fscape.graph.impl.{MultiOutImpl, SingleOutImpl, ZeroOutImpl}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions
import scala.runtime.ScalaRunTime

///** The raw UGen information as it is found in a final `UGenGraph`. */
//trait RawUGen {
//  def numInputs : Int
//  def numOutputs: Int
//}

/** A UGen during graph building process is a more
  * rich thing than `RawUGen`: it implements equality
  * based on `isIndividual` status and may be omitted
  * from the final graph based on `hasSideEffect` status.
  */
sealed trait UGen extends Product {
  // initialize this first, so that debug printing in `addUGen` can use the hash code
  override val hashCode: Int = if (isIndividual) super.hashCode() else ScalaRunTime._hashCode(this)

  override def toString: String = inputs.mkString(s"$name(", ", ", ")")

  def name      : String

  def inputs      : Vec[UGenIn]

  def numInputs : Int = inputs.size
  def numOutputs: Int

  // the full UGen spec:
  // name, inputs
  override final def productPrefix: String = "UGen"
  final def productArity: Int = 2

  final def productElement(n: Int): Any = (n: @switch) match {
    case 0 => name
    case 1 => inputs
    case _ => throw new java.lang.IndexOutOfBoundsException(n.toString)
  }

  final def canEqual(x: Any): Boolean = x.isInstanceOf[UGen]

  override def equals(x: Any): Boolean = (this eq x.asInstanceOf[AnyRef]) || (!isIndividual && (x match {
    case u: UGen =>
      u.name == name && u.inputs == inputs && u.canEqual(this)
    case _ => false
  }))

  def isIndividual : Boolean
  def hasSideEffect: Boolean
}

object UGen {
  object SingleOut {
    def apply(source: UGenSource.SingleOut, inputs: Vec[UGenIn], isIndividual: Boolean = false,
              hasSideEffect: Boolean = false)(implicit b: UGenGraph.Builder): SingleOut = {
      val res = new SingleOutImpl(source, inputs, isIndividual = isIndividual, hasSideEffect = hasSideEffect)
      b.addUGen(res)
      res
    }
  }
  /** A SingleOutUGen is a UGen which has exactly one output, and
    * hence can directly function as input to another UGen without expansion.
    */
  trait SingleOut extends graph.UGenProxy with UGen {
    final def numOutputs = 1
    final def outputIndex = 0
    final def ugen: UGen = this

    def source: UGenSource.SingleOut
  }

  object ZeroOut {
    def apply(source: UGenSource.ZeroOut, inputs: Vec[UGenIn], isIndividual: Boolean = false)
             (implicit b: UGenGraph.Builder): ZeroOut = {
      val res = new ZeroOutImpl(source, inputs, isIndividual = isIndividual)
      b.addUGen(res)
      res
    }
  }
  trait ZeroOut extends UGen {
    final def numOutputs    = 0
    final def hasSideEffect = true  // implied by having no outputs

    def source: UGenSource.ZeroOut
  }

  object MultiOut {
    def apply(source: UGenSource.MultiOut, inputs: Vec[UGenIn], numOutputs: Int,
              isIndividual: Boolean = false, hasSideEffect: Boolean = false)
             (implicit b: UGenGraph.Builder): MultiOut = {
      val res = new MultiOutImpl(source, numOutputs = numOutputs, inputs = inputs, isIndividual = isIndividual,
        hasSideEffect = hasSideEffect)
      b.addUGen(res)
      res
    }
  }
  /** A class for UGens with multiple outputs. */
  trait MultiOut extends graph.UGenInGroup with UGen {

    final def unwrap(i: Int): UGenInLike = graph.UGenOutProxy(this, i % numOutputs)

    def outputs: Vec[UGenIn] = Vector.tabulate(numOutputs)(ch => graph.UGenOutProxy(this, ch))

    def source: UGenSource.MultiOut

    private[fscape] final def unbubble: UGenInLike = if (numOutputs == 1) outputs(0) else this
  }
}

object UGenInLike {
  implicit def expand(ge: GE)(implicit b: UGenGraph.Builder): UGenInLike = ge.expand
}
sealed trait UGenInLike extends GE {
  private[fscape] def outputs: Vec[UGenInLike]
  private[fscape] def unbubble: UGenInLike

  /** Returns the UGenInLike element of index i
    * regarding the ungrouped representation. Note
    * that for efficiency reasons this method will
    * automatically wrap the index around numElements!
    */
  private[fscape] def unwrap(i: Int): UGenInLike
  private[fscape] def flatOutputs: Vec[UGenIn]

  // ---- GE ----
  final private[fscape] def expand(implicit b: UGenGraph.Builder): UGenInLike = this
}

/** An element that can be used as an input to a UGen.
  * This is after multi-channel-expansion, hence implementing
  * classes are SingleOutUGen, UGenOutProxy, ControlOutProxy, and Constant.
  */
sealed trait UGenIn extends UGenInLike {
  private[fscape] def outputs: Vec[UGenIn] = Vector(this)
  private[fscape] final def unwrap(i: Int): UGenInLike = this

  // don't bother about the index
  private[fscape] final def flatOutputs: Vec[UGenIn] = Vector(this)
  private[fscape] final def unbubble   : UGenInLike  = this
}

package graph {

  import akka.stream.scaladsl
  import de.sciss.fscape.stream.{BufD, BufI, OutD, OutI, StreamIn}

  object UGenInGroup {
    private final val emptyVal = new Apply(Vector.empty)
    def empty: UGenInGroup = emptyVal
    def apply(xs: Vec[UGenInLike]): UGenInGroup = new Apply(xs)

    private final case class Apply(outputs: Vec[UGenInLike]) extends UGenInGroup {
      override def productPrefix = "UGenInGroup"

      private[fscape] def numOutputs: Int = outputs.size
      private[fscape] def unwrap(i: Int): UGenInLike = outputs(i % outputs.size)
      private[fscape] def unbubble: UGenInLike = this

      override def toString = outputs.mkString("UGenInGroup(", ",", ")")
    }
  }
  sealed trait UGenInGroup extends UGenInLike {
    private[fscape] def outputs: Vec[UGenInLike]
    private[fscape] def numOutputs: Int
    private[fscape] final def flatOutputs: Vec[UGenIn] = outputs.flatMap(_.flatOutputs)
  }

  sealed trait UGenProxy extends UGenIn {
    def ugen: UGen
    def outputIndex: Int
  }

  object Constant {
    def unapply(c: Constant): Option[Double] = Some(c.doubleValue)
  }
  /** A scalar constant used as an input to a UGen. */
  sealed trait Constant extends UGenIn with StreamIn {
    def doubleValue : Double
    def intValue    : Int

    def toDouble(implicit b: stream.Builder): OutD = b.add(scaladsl.Source.single(BufD(doubleValue))).out
    def toInt   (implicit b: stream.Builder): OutI = b.add(scaladsl.Source.single(BufI(intValue   ))).out
  }
  object ConstantI {
    final val C0  = new ConstantI(0)
    final val C1  = new ConstantI(1)
    final val Cm1 = new ConstantI(-1)
  }
  final case class ConstantI(value: Int) extends Constant {
    def doubleValue = value.toDouble
    def intValue    = value
    override def toString = value.toString
  }
  final case class ConstantL(value: Long) extends Constant {
    def doubleValue = value.toDouble
    def intValue    = {
      val res = value.toInt
      if (res != value) throw new ArithmeticException(s"Long $value exceeds Int range")
      res
    }
    override def toString = value.toString
  }
  object ConstantD {
    final val C0  = new ConstantD(0)
    final val C1  = new ConstantD(1)
    final val Cm1 = new ConstantD(-1)
  }
  final case class ConstantD(value: Double) extends Constant {
    def doubleValue = value
    def intValue    = {
      if (value.isNaN) throw new ArithmeticException("NaN cannot be translated to Int")
      val r = math.rint(value)
      if (r < Int.MinValue || r > Int.MaxValue)
        throw new ArithmeticException(s"Double $value exceeds Int range")
      r.toInt
    }
    override def toString = value.toString
  }

  //  /** A ControlOutProxy is similar to a UGenOutProxy in that it denotes
//    * an output channel of a control UGen. However it refers to a control-proxy
//    * instead of a real control ugen, since the proxies are synthesized into
//    * actual ugens only at the end of a synth graph creation, in order to
//    * clump several controls together. ControlOutProxy instance are typically
//    * returned from the ControlProxyFactory class, that is, using the package
//    * implicits, from calls such as "myControl".kr.
//    */
//  final case class ControlUGenOutProxy(source: ControlProxyLike, outputIndex: Int)
//    extends UGenIn {
//
//    override def toString = s"$source.\\($outputIndex)"
//  }

  /** A UGenOutProxy refers to a particular output of a multi-channel UGen.
    * A sequence of these form the representation of a multi-channel-expanded
    * UGen.
    */
  final case class UGenOutProxy(ugen: UGen.MultiOut, outputIndex: Int)
    extends UGenIn with UGenProxy {

    override def toString =
      if (ugen.numOutputs == 1) ugen.toString else s"$ugen.\\($outputIndex)"
  }
}