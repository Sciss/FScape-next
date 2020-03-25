/*
 *  GEOps.scala
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

package de.sciss.fscape

import de.sciss.fscape.graph.BinaryOp._
import de.sciss.fscape.graph.UnaryOp._
import de.sciss.fscape.graph.{BinaryOp, ChannelProxy, Clip, ComplexBinaryOp, ComplexUnaryOp, Concat, Constant, Distinct, Drop, Elastic, FilterSeq, Fold, Length, MatchLen, Metro, Poll, ResizeWindow, RunningMax, RunningMin, RunningProduct, RunningSum, Take, TakeRight, UnaryOp, UnzipWindow, Wrap, ZipWindow}
import de.sciss.optional.Optional

/** `GEOps1` are operations for graph elements (`GE`). Instead of having these operations directly defined
  * in each element, which is a huge list, they appear here as extension methods. `GEOps1` are unary
  * operators, whereas `GEOps2` are binary and n-ary operators.
  *
  * @see [[GE]]
  * @see [[GEOps2]]
  */
final class GEOps1(val `this`: GE) extends AnyVal { me =>
  import me.{`this` => g}

  /** Creates a proxy that represents a specific output channel of the element.
    *
    * @param index  channel-index, zero-based. Indices which are greater than or equal
    *               to the number of outputs are wrapped around.
    * @return a monophonic element that represents the given channel of the receiver
    */
  def out(index: Int): GE = ChannelProxy(g, index)

  @inline private def unOp(op: UnaryOp.Op): GE = op.make(g)

  private def single(in: GE): GE =
    in match {
      case c: Constant  => c
      case _            => in.head
    }

  // unary ops
  def unary_-   : GE  = unOp(Neg       )
  def unary_!   : GE  = unOp(Not       )
  // def bitNot: GE = ...
  def abs       : GE  = unOp(Abs       )
  // def toFloat: GE = ...
  // def toInteger: GE = ...
  def ceil      : GE  = unOp(Ceil      )
  def floor     : GE  = unOp(Floor     )
  def frac      : GE  = unOp(Frac      )
  def signum    : GE  = unOp(Signum    )
  def squared   : GE  = unOp(Squared   )
  def cubed     : GE  = unOp(Cubed     )
  def sqrt      : GE  = unOp(Sqrt      )
  def exp       : GE  = unOp(Exp       )
  def reciprocal: GE  = unOp(Reciprocal)
  def midiCps   : GE  = unOp(Midicps   )
  def cpsMidi   : GE  = unOp(Cpsmidi   )
  def midiRatio : GE  = unOp(Midiratio )
  def ratioMidi : GE  = unOp(Ratiomidi )
  def dbAmp     : GE  = unOp(Dbamp     )
  def ampDb     : GE  = unOp(Ampdb     )
  def octCps    : GE  = unOp(Octcps    )
  def cpsOct    : GE  = unOp(Cpsoct    )
  def log       : GE  = unOp(Log       )
  def log2      : GE  = unOp(Log2      )
  def log10     : GE  = unOp(Log10     )
  def sin       : GE  = unOp(Sin       )
  def cos       : GE  = unOp(Cos       )
  def tan       : GE  = unOp(Tan       )
  def asin      : GE  = unOp(Asin      )
  def acos      : GE  = unOp(Acos      )
  def atan      : GE  = unOp(Atan      )
  def sinh      : GE  = unOp(Sinh      )
  def cosh      : GE  = unOp(Cosh      )
  def tanh      : GE  = unOp(Tanh      )
  // def rand : GE              = UnOp.make( 'rand, this )
  // def rand2 : GE             = UnOp.make( 'rand2, this )
  // def linRand : GE           = UnOp.make( 'linrand, this )
  // def bilinRand : GE         = UnOp.make( 'bilinrand, this )
  // def sum3Rand : GE          = UnOp.make( 'sum3rand, this )
  //  def distort   : GE  = unOp(Distort   )
  //  def softClip  : GE  = unOp(Softclip  )

  // def coin : GE              = UnOp.make( 'coin, this )
  // def even : GE              = UnOp.make( 'even, this )
  // def odd : GE               = UnOp.make( 'odd, this )
  // def rectWindow : GE        = UnOp.make( 'rectWindow, this )
  // def hannWindow : GE         = UnOp.make( 'hanWindow, this )
  // def welchWindow : GE         = UnOp.make( 'sum3rand, this )
  // def triWindow : GE         = UnOp.make( 'triWindow, this )
  //  def ramp      : GE  = unOp(Ramp      )
  //  def sCurve    : GE  = unOp(Scurve    )

  // def isPositive : GE        = UnOp.make( 'isPositive, this )
  // def isNegative : GE        = UnOp.make( 'isNegative, this )
  // def isStrictlyPositive : GE= UnOp.make( 'isStrictlyPositive, this )
  // def rho : GE               = UnOp.make( 'rho, this )
  // def theta : GE             = UnOp.make( 'theta, this )

  def isNaN: GE = unOp(IsNaN)

  def nextPowerOfTwo: GE = unOp(NextPowerOfTwo)

  def elastic(n: GE = 1): GE = Elastic(g, n)

  // ---- sequence ----

  /** Concatenates another signal to this (finite) signal. */
  def ++ (that: GE): GE = Concat(g, that)

  /** Prepends a single frame. */
  def +: (elem: GE): GE = prepended(elem)

  /** Appends a single frame. */
  def :+ (elem: GE): GE = appended(elem)

  /** Appends a single frame. */
  def appended(elem: GE): GE = Concat(g, single(elem))

  /** Concatenates another signal to this (finite) signal. */
  def concat(that: GE): GE = Concat(g, that)

  def contains(elem: GE): GE = indexOf(elem) >= 0

  def distinct: GE = Distinct(g)

  /** Drops the first `length` elements of this signal. */
  def drop(length: GE): GE = Drop(in = g, length = length)

  def dropRight(length: GE): GE = ??? // Drop(in = g, length = length)

  /** XXX TODO --- this doesn't have a dedicated UGen yet. Callers must assume an extra block of delay */
  def dropWhile(gate: GE): GE = {
//    val off = !gate
//    val p   = SetResetFF(trig = off)
//    FilterSeq(g.elastic(), p)
    ???
  }

  def endsWith(that: GE): GE = ??? // BinOp(BinOp.SeqEndsWith[A, A](), x, that)

  def filter(p: GE): GE = FilterSeq(g, p)

  def filterNot(p: GE): GE = filter(!p)

  /** Returns the first element for which the predicate holds, or an empty stream if no predicate holds. */
  def find(p: GE): GE = ???

  /** Returns the last element for which the predicate holds, or an empty stream if no predicate holds. */
  def findLast(p: GE): GE = ???

    /** Outputs the first element of this signal, then terminates. */
  def head: GE = take(1)

  def indexOf(elem: GE): GE = indexOfSlice(single(elem))

  def indexOf(elem: GE, from: GE): GE = indexOfSlice(single(elem), from)

  def indexOfSlice(that: GE): GE = indexOfSlice(that, from = 0)

  def indexOfSlice(that: GE, from: GE): GE = ???

  def indexWhere(p: GE): GE = ???

  def indices: GE = ???

  def init: GE = dropRight(1)

  def isDefinedAt(index: GE): GE = (index >= 0) & (index < size)

  def isEmpty: GE = size sig_== 0L

  /** Outputs the last element of this (finite) signal, then terminates. */
  def last: GE = takeRight(1)

  def lastIndexOf(elem: GE): GE = lastIndexOfSlice(single(elem))

  /** The index of the last occurrence of `elem` at or before `end` in this sequence, or `-1` if not found */
  def lastIndexOf(elem: GE, end: GE): GE = lastIndexOfSlice(single(elem), end)

  /** Last index where this sequence contains `that` sequence as a slice, or `-1` if not found */
  def lastIndexOfSlice(that: GE): GE = ???

  /** Last index at or before `end` where this sequence contains `that` sequence as a slice, or `-1` if not found */
  def lastIndexOfSlice(that: GE, end: GE): GE = ???

  def length: GE = Length(g)

  /** Returns the maximum value cross all elements (or an empty stream if the input is empty) */
  def maximum: GE = RunningMax(g).last

  /** Returns the minimum value cross all elements (or an empty stream if the input is empty) */
  def minimum: GE = RunningMin(g).last

  def nonEmpty: GE = size > 0L

  def padTo(len: GE, elem: GE = 0.0): GE = ???

  def patch(from: GE, other: GE, replaced: GE): GE = ???

  /** Prepends a single frame. */
  def prepended(elem: GE): GE = Concat(single(elem), g)

  def product: GE = RunningProduct(g).last

  def reverse: GE = ???

  def size: GE = length

  def slice(from: GE, until: GE): GE = {
    val sz = until - from
    g.drop(from).take(sz)
  }

  def sorted: GE = ???

  def splitAt(n: GE): (GE, GE) = {
    val _1 = take(n)
    val _2 = drop(n)
    (_1, _2)
  }

  def startsWith(that: GE, offset: GE = 0L): GE = ???

  def sum: GE = RunningSum(g).last

  def tail: GE = drop(1)

  /** Takes at most `length` elements of this signal, then terminates. */
  def take(length: GE): GE = Take(in = g, length = length)

  /** Takes at most the last `length` elements of this (finite) signal. */
  def takeRight(length: GE): GE = TakeRight(in = g, length = length)

  /** XXX TODO --- this doesn't have a dedicated UGen yet. Callers must assume an extra block of delay */
  def takeWhile(gate: GE): GE = {
//    val off = !gate
//    val p   = -SetResetFF(trig = off) + 1
//    FilterSeq(g.elastic(), p)
    ???
  }

  def unzip: (GE, GE) = {
    val u = UnzipWindow(g)
    (u.out(0), u.out(1))
  }

  /** A new sequence equal to this sequence with one single replaced `elem` at `index`.
    * If the index lies outside the sequence, the behavior is undefined.
    */
  def updated(index: GE, elem: GE): GE = patch(index, single(elem), 1)

  def zip(that: GE): GE = ZipWindow(g, that)
}

/** `GEOps2` are operations for graph elements (`GE`). Instead of having these operations directly defined
  * in each element, which is a huge list, they appear here as extension methods. `GEOps1` are unary
  * operators, whereas `GEOps2` are binary and n-ary operators.
  *
  * @see [[GE]]
  * @see [[GEOps1]]
  */
final class GEOps2(val `this`: GE) extends AnyVal { me =>
  import me.{`this` => g}

//  def madd(mul: GE, add: GE): GE = MulAdd(g, mul, add)
//
//  def flatten               : GE = Flatten(g)

  /** Polls a single value from the element. */
  def poll: Poll = poll()

  /** Polls a single value from the element, and prints it with a given label. */
  def poll(label: String): Poll = poll(gate = 0, label = Some(label))

  /** Polls the output values of this graph element, and prints the result to the console.
    * This is a convenient method for wrapping this graph element in a `Poll` UGen.
    *
    * @param   gate     a gate signal for the printing. If this is a constant, it is
    *                   interpreted as a period and a `Metro` generator with
    *                   this period is used. Will be renamed to '''gate''' in the next
    *                   major version
    * @param   label    a string to print along with the values, in order to identify
    *                   different polls. Using the special label `"#auto"` (default) will generated
    *                   automatic useful labels using information from the polled graph element
    * @see  [[de.sciss.fscape.graph.Poll]]
    */
  def poll(gate: GE = 0, label: Optional[String] = None): Poll = {
    val gate1 = gate match {
      case c: Constant  => Metro(c)
      case other        => other
    }
    Poll(in = g, gate = gate1, label = label.getOrElse {
      val str = g.toString
      val i   = str.indexOf('(')
      if (i >= 0) str.substring(0, i)
      else {
        val j = str.indexOf('@')
        if (j >= 0) str.substring(0, j)
        else str
      }
    })
  }

  // binary ops
  @inline private def binOp(op: BinaryOp.Op, b: GE): GE = op.make(g, b)

  def +       (b: GE): GE = binOp(Plus    , b)
  def -       (b: GE): GE = binOp(Minus   , b)
  def *       (b: GE): GE = binOp(Times   , b)
  // def div(b: GE): GE = ...
  def /       (b: GE): GE = binOp(Div     , b)
  def %       (b: GE): GE = binOp(Mod     , b)
  def sig_==  (b: GE): GE = binOp(Eq      , b)
  def sig_!=  (b: GE): GE = binOp(Neq     , b)
  def <       (b: GE): GE = binOp(Lt      , b)
  def >       (b: GE): GE = binOp(Gt      , b)
  def <=      (b: GE): GE = binOp(Leq     , b)
  def >=      (b: GE): GE = binOp(Geq     , b)
  def min     (b: GE): GE = binOp(Min     , b)
  def max     (b: GE): GE = binOp(Max     , b)
  def &       (b: GE): GE = binOp(BitAnd  , b)
  def |       (b: GE): GE = binOp(BitOr   , b)
  def ^       (b: GE): GE = binOp(BitXor  , b)
  // def lcm(b: GE): GE = ...
  // def gcd(b: GE): GE = ...

  def roundTo (b: GE): GE = binOp(RoundTo , b)
  def roundUpTo (b: GE): GE = binOp(RoundUpTo, b)
  def trunc   (b: GE): GE = binOp(Trunc   , b)
  def atan2   (b: GE): GE = binOp(Atan2   , b)
  def hypot   (b: GE): GE = binOp(Hypot   , b)
  def hypotApx(b: GE): GE = binOp(Hypotx  , b)

  /** '''Warning:''' Unlike a normal power operation, the signum of the
    * left operand is always preserved. I.e. `DC.kr(-0.5).pow(2)` will
    * not output `0.25` but `-0.25`. This is to avoid problems with
    * floating point noise and negative input numbers, so
    * `DC.kr(-0.5).pow(2.001)` does not result in a `NaN`, for example.
    */
  def pow     (b: GE): GE = binOp(Pow     , b)

  // def <<(b: GE): GE = ...
  // def >>(b: GE): GE = ...
  // def unsgnRghtShift(b: GE): GE = ...
  // def fill(b: GE): GE = ...

  def ring1   (b: GE): GE = binOp(Ring1   , b)
  def ring2   (b: GE): GE = binOp(Ring2   , b)
  def ring3   (b: GE): GE = binOp(Ring3   , b)
  def ring4   (b: GE): GE = binOp(Ring4   , b)
  def difSqr  (b: GE): GE = binOp(Difsqr  , b)
  def sumSqr  (b: GE): GE = binOp(Sumsqr  , b)
  def sqrSum  (b: GE): GE = binOp(Sqrsum  , b)
  def sqrDif  (b: GE): GE = binOp(Sqrdif  , b)
  def absDif  (b: GE): GE = binOp(Absdif  , b)
  def thresh  (b: GE): GE = binOp(Thresh  , b)
  def amClip  (b: GE): GE = binOp(Amclip  , b)
  def scaleNeg(b: GE): GE = binOp(Scaleneg, b)
  def clip2   (b: GE): GE = binOp(Clip2   , b)
  def excess  (b: GE): GE = binOp(Excess  , b)
  def fold2   (b: GE): GE = binOp(Fold2   , b)
  def wrap2   (b: GE): GE = binOp(Wrap2   , b)
//  def firstArg(b: GE): GE = binOp(Firstarg, b)

  /** Truncates or extends the first operand to
    * match the length of `b`. This uses
    * the `SecondArg` operator with operands reversed.
    */
  def matchLen(b: GE): GE = MatchLen(g, b)

  // def rrand(b: GE): GE    = ...
  // def exprrand(b: GE): GE = ...

  def clip(low: GE = 0.0, high: GE = 1.0): GE = Clip(g, low, high)
  def fold(low: GE = 0.0, high: GE = 1.0): GE = Fold(g, low, high)
  def wrap(low: GE = 0.0, high: GE = 1.0): GE = Wrap(g, low, high)

  def linLin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
    // XXX TODO
    // LinLin(/* rate, */ g, inLow, inHigh, outLow, outHigh)
    (g - inLow) / (inHigh - inLow) * (outHigh - outLow) + outLow
  }

  def linExp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
    // XXX TODO
    // LinExp(g.rate, g, inLow, inHigh, outLow, outHigh) // should be highest rate of all inputs? XXX
    val outRatio  = outHigh / outLow
    val outRatioP = BinaryOp.SecondArg.make(g, outRatio)
    outRatioP.pow((g - inLow) / (inHigh - inLow)) * outLow
  }

  def expLin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE =
    (g / inLow).log / (inHigh / inLow).log * (outHigh - outLow) + outLow

  def expExp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
    val outRatio  = outHigh / outLow
    val outRatioP = BinaryOp.SecondArg.make(g, outRatio)
    outRatioP.pow((g / inLow).log / (inHigh / inLow).log) * outLow
  }

  /** Enables operators for an assumed complex signal. */
  def complex: GEComplexOps = new GEComplexOps(g)
}

final class GEComplexOps(val `this`: GE) extends AnyVal { me =>
  import me.{`this` => g}

  @inline private def cUnOp(op: ComplexUnaryOp.Op): GE = op.make(g)

  import ComplexUnaryOp._

  // unary ops
  def abs       : GE  = cUnOp(Abs        )
  def squared   : GE  = cUnOp(Squared    )
  def cubed     : GE  = cUnOp(Cubed      )
  def exp       : GE  = cUnOp(Exp        )
  def reciprocal: GE  = cUnOp(Reciprocal )
  def log       : GE  = cUnOp(Log        )
  def log2      : GE  = cUnOp(Log2       )
  def log10     : GE  = cUnOp(Log10      )
  def conj      : GE  = cUnOp(Conj       )
  def absSquared: GE  = cUnOp(AbsSquared )

  @inline private def cBinOp(op: ComplexBinaryOp.Op, b: GE): GE = op.make(g, b)

  import ComplexBinaryOp._

  // binary ops
  def + (b: GE): GE  = cBinOp(Plus , b)
  def - (b: GE): GE  = cBinOp(Minus, b)
  def * (b: GE): GE  = cBinOp(Times, b)

  // unary to real
  def mag: GE = {
    // ChannelProxy(UnzipWindow(abs), 0) // BROKEN
    ResizeWindow(abs, size = 2, start = 0, stop = -1)
  }

  def phase: GE  = {
    // val unzip = UnzipWindow(g) // BROKEN
    val re    = real // ChannelProxy(unzip, 0) // BROKEN
    val im    = imag // ChannelProxy(unzip, 1) // BROKEN
    im atan2 re
  }

  def real: GE = {
    // ChannelProxy(UnzipWindow(g  ), 0)  // BROKEN
    ResizeWindow(g, size = 2, start = 0, stop = -1)
  }

  def imag: GE = {
    // ChannelProxy(UnzipWindow(g  ), 1)  // BROKEN
    ResizeWindow(g, size = 2, start = 1, stop = 0)
  }
}