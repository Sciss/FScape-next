/*
 *  GEOps.scala
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

package de.sciss.fscape

import de.sciss.fscape.graph.BinaryOp._
import de.sciss.fscape.graph.UnaryOp._
import de.sciss.fscape.graph.{BinaryOp, ChannelProxy, Clip, ComplexBinaryOp, ComplexUnaryOp, Concat, Constant, Drop, Elastic, FilterSeq, Fold, Metro, Poll, SetResetFF, Take, TakeRight, UnaryOp, UnzipWindow, Wrap, ZipWindow}
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

  /** Takes at most `length` elements of this signal, then terminates. */
  def take(length: GE): GE = Take(in = g, length = length)

  /** Takes at most the last `length` elements of this (finite) signal. */
  def takeRight(length: GE): GE = TakeRight(in = g, length = length)

  /** Drops the first `length` elements of this signal. */
  def drop(length: GE): GE = Drop(in = g, length = length)

  /** XXX TODO --- this doesn't have a dedicated UGen yet. Callers must assume an extra block of delay */
  def dropWhile(gate: GE): GE = {
    val off = !gate
    val p   = SetResetFF(trig = off)
    FilterSeq(g.elastic(), p)
  }

  /** XXX TODO --- this doesn't have a dedicated UGen yet. Callers must assume an extra block of delay */
  def takeWhile(gate: GE): GE = {
    val off = !gate
    val p   = -SetResetFF(trig = off) + 1
    FilterSeq(g.elastic(), p)
  }

  //  /** Drops the last `len` elements of this (finite) signal. */
//  def dropRight(len: GE): GE = ...
//  def init: GE = DropRight(in = g, len = 1)

  /** Outputs the first element of this signal, then terminates. */
  def head: GE = take(1)

  /** Outputs the last element of this (finite) signal, then terminates. */
  def last: GE = takeRight(1)

  def tail: GE = Drop(in = g, length = 1)

  /** Concatenates another signal to this (finite) signal. */
  def ++ (that: GE): GE = Concat(g, that)

  /** Appends a single frame. */
  def :+ (that: Constant): GE = Concat(g, that)

  /** Prepends a single frame. */
  def +: (that: Constant): GE = Concat(that, g)

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

  def poll: Poll = poll()

  /** Polls the output values of this graph element, and prints the result to the console.
    * This is a convenient method for wrapping this graph element in a `Poll` UGen.
    *
    * @param   trig     a signal to trigger the printing. If this is a constant, it is
    *                   interpreted as a period and a `Metro` generator with
    *                   this period is used.
    * @param   label    a string to print along with the values, in order to identify
    *                   different polls. Using the special label `"#auto"` (default) will generated
    *                   automatic useful labels using information from the polled graph element
    * @see  [[de.sciss.fscape.graph.Poll]]
    */
  def poll(trig: GE = 5000, label: Optional[String] = None): Poll = {
    val trig1 = trig match {
      case c: Constant  => Metro(c)
      case other        => other
    }
    Poll(in = g, trig = trig1, label = label.getOrElse {
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
  def matchLen(b: GE): GE = SecondArg.make(b, g)

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
  def mag       : GE  = ChannelProxy(UnzipWindow(abs), 0)
  def phase     : GE  = {
    val unzip = UnzipWindow(g)
    val re    = ChannelProxy(unzip, 0)
    val im    = ChannelProxy(unzip, 1)
    im atan2 re // XXX TODO --- correct?
  }

  def real      : GE  = ChannelProxy(UnzipWindow(g  ), 0)
  def imag      : GE  = ChannelProxy(UnzipWindow(g  ), 1)
}