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

import de.sciss.fscape.graph.{UGenInGroup, UGenOutProxy, UGenProxy}
import de.sciss.fscape.graph.impl.{MultiOutImpl, SingleOutImpl, ZeroOutImpl}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.language.implicitConversions

/** A UGen during graph building process is a more
  * rich thing than `RawUGen`: it implements equality
  * based on `isIndividual` status and may be omitted
  * from the final graph based on `hasSideEffect` status.
  */
sealed trait UGen extends Product {
// !!! WE CURRENTLY DISABLE STRUCTURAL EQUALITY
//  // initialize this first, so that debug printing in `addUGen` can use the hash code
//  override val hashCode: Int = if (isIndividual) super.hashCode() else scala.runtime.ScalaRunTime._hashCode(this)

  override def toString: String = inputs.mkString(s"$name(", ", ", ")")

  def name      : String

  def inputs    : Vec[UGenIn]

  /** Additional UGen arguments that are not of type `UGenIn`.
    * These are included to achieve correct equality
    * (also as we do not transcode unary/binary operator ids
    * into special indices)
    */
  protected def rest: Any

  def numInputs : Int = inputs.size
  def numOutputs: Int

  // the full UGen spec:
  // name, inputs
  override final def productPrefix: String = "UGen"
  final def productArity: Int = 3

  final def productElement(n: Int): Any = (n: @switch) match {
    case 0 => name
    case 1 => inputs
    case 2 => rest
    case _ => throw new java.lang.IndexOutOfBoundsException(n.toString)
  }

  final def canEqual(x: Any): Boolean = x.isInstanceOf[UGen]

// !!! WE CURRENTLY DISABLE STRUCTURAL EQUALITY
//  override def equals(x: Any): Boolean = (this eq x.asInstanceOf[AnyRef]) || (!isIndividual && (x match {
//    case u: UGen =>
//      u.name == name && u.inputs == inputs && u.rest == rest && u.canEqual(this)
//    case _ => false
//  }))

  def isIndividual : Boolean
  def hasSideEffect: Boolean
}

object UGen {
  object SingleOut {
    def apply(source: UGenSource.SingleOut, inputs: Vec[UGenIn], rest: Any = (), isIndividual: Boolean = false,
              hasSideEffect: Boolean = false)(implicit b: UGenGraph.Builder): SingleOut = {
      val res = new SingleOutImpl(source = source, inputs = inputs, rest = rest,
        isIndividual = isIndividual, hasSideEffect = hasSideEffect)
      b.addUGen(res)
      res
    }
  }
  /** A SingleOutUGen is a UGen which has exactly one output, and
    * hence can directly function as input to another UGen without expansion.
    */
  trait SingleOut extends UGenProxy with UGen {
    final def numOutputs = 1
    final def outputIndex = 0
    final def ugen: UGen = this

    def source: UGenSource.SingleOut
  }

  object ZeroOut {
    def apply(source: UGenSource.ZeroOut, inputs: Vec[UGenIn], rest: Any = (), isIndividual: Boolean = false)
             (implicit b: UGenGraph.Builder): ZeroOut = {
      val res = new ZeroOutImpl(source = source, inputs = inputs, rest = rest, isIndividual = isIndividual)
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
              rest: Any = (), isIndividual: Boolean = false, hasSideEffect: Boolean = false)
             (implicit b: UGenGraph.Builder): MultiOut = {
      val res = new MultiOutImpl(source = source, numOutputs = numOutputs, inputs = inputs,
        rest = rest, isIndividual = isIndividual,
        hasSideEffect = hasSideEffect)
      b.addUGen(res)
      res
    }
  }
  /** A class for UGens with multiple outputs. */
  trait MultiOut extends UGenInGroup with UGen {

    final def unwrap(i: Int): UGenInLike = UGenOutProxy(this, i % numOutputs)

    def outputs: Vec[UGenIn] = Vector.tabulate(numOutputs)(ch => UGenOutProxy(this, ch))

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

  import akka.stream.{Outlet, scaladsl}
  import de.sciss.fscape.stream.{BufD, BufI, BufL, BufLike, OutD, OutI, OutL, StreamIn}

  object UGenInGroup {
    private final val emptyVal = Apply(Vector.empty)
    def empty: UGenInGroup = emptyVal
    def apply(xs: Vec[UGenInLike]): UGenInGroup = Apply(xs)

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
    def longValue   : Long

    def value: Any

    def toDouble(implicit b: stream.Builder): OutD = b.add(scaladsl.Source.single(BufD(doubleValue))).out
    def toInt   (implicit b: stream.Builder): OutI = b.add(scaladsl.Source.single(BufI(intValue   ))).out
    def toLong  (implicit b: stream.Builder): OutL = b.add(scaladsl.Source.single(BufL(longValue  ))).out
  }
  object ConstantI {
    final val C0  = new ConstantI(0)
    final val C1  = new ConstantI(1)
    final val Cm1 = new ConstantI(-1)
  }
  final case class ConstantI(value: Int) extends Constant {
    def toAny(implicit b: stream.Builder): Outlet[BufLike] = toInt.as[BufLike]

    def isInt   : Boolean = true
    def isLong  : Boolean = false
    def isDouble: Boolean = false

    type Elem = BufI
    def toElem(implicit b: stream.Builder): OutI = toInt

    def doubleValue: Double = value.toDouble
    def intValue   : Int    = value
    def longValue  : Long   = value.toLong

    override def toString = value.toString
  }
  object ConstantD {
    final val C0  = new ConstantD(0)
    final val C1  = new ConstantD(1)
    final val Cm1 = new ConstantD(-1)
  }
  final case class ConstantD(value: Double) extends Constant {
    def toAny(implicit b: stream.Builder): Outlet[BufLike] = toDouble.as[BufLike]

    def isInt   : Boolean = false
    def isLong  : Boolean = false
    def isDouble: Boolean = true

    type Elem = BufD
    def toElem(implicit b: stream.Builder): OutD = toDouble

    def doubleValue: Double = value
    def intValue   : Int    = {
      if (value.isNaN) throw new ArithmeticException("NaN cannot be translated to Int")
      val r = math.rint(value)
      if (r < Int.MinValue || r > Int.MaxValue)
        throw new ArithmeticException(s"Double $value exceeds Int range")
      r.toInt
    }
    def longValue  : Long = {
      if (value.isNaN) throw new ArithmeticException("NaN cannot be translated to Long")
      val r = math.round(value)
      r
    }

    override def toString = value.toString
  }
  object ConstantL {
    final val C0  = new ConstantI(0)
    final val C1  = new ConstantI(1)
    final val Cm1 = new ConstantI(-1)
  }
  final case class ConstantL(value: Long) extends Constant {
    def toAny(implicit b: stream.Builder): Outlet[BufLike] = toLong.as[BufLike]

    def isInt   : Boolean = false
    def isLong  : Boolean = true
    def isDouble: Boolean = false

    type Elem = BufL
    def toElem(implicit b: stream.Builder): OutL = toLong

    def doubleValue: Double = value.toDouble
    def intValue   : Int    = {
      val res = value.toInt
      if (res != value) throw new ArithmeticException(s"Long $value exceeds Int range")
      res
    }
    def longValue  : Long = value

    override def toString = value.toString
  }

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