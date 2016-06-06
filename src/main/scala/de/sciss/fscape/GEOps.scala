/*
 *  GEOps.scala
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

import de.sciss.fscape.graph.{BinaryOp, ChannelProxy, Constant, Impulse, Poll, TakeRight, UnaryOp}
import de.sciss.optional.Optional

final class GEOps(val `this`: GE) extends AnyVal { me =>
  import me.{`this` => g}

  /** Creates a proxy that represents a specific output channel of the element.
    *
    * @param index  channel-index, zero-based. Indices which are greater than or equal
    *               to the number of outputs are wrapped around.
    *
    * @return a monophonic element that represents the given channel of the receiver
    */
  def `\\`(index: Int)      : GE = ChannelProxy(g, index)

//  def madd(mul: GE, add: GE): GE = MulAdd(g, mul, add)
//
//  def flatten               : GE = Flatten(g)

  def poll: Poll = poll()

  /** Polls the output values of this graph element, and prints the result to the console.
    * This is a convenient method for wrapping this graph element in a `Poll` UGen.
    *
    * @param   trig     a signal to trigger the printing. If this is a constant, it is
    *                   interpreted as a normalized frequency and an `Impulse` generator with
    *                   this normalized frequency is used.
    * @param   label    a string to print along with the values, in order to identify
    *                   different polls. Using the special label `"#auto"` (default) will generated
    *                   automatic useful labels using information from the polled graph element
    *
    * @see  [[de.sciss.fscape.graph.Poll]]
    */
  def poll(trig: GE = 2e-4, label: Optional[String] = None): Poll = {
    val trig1 = trig match {
      case Constant(freqN)  => Impulse(freqN)
      case other            => other
    }
    Poll(trig1, g, label.getOrElse {
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

  import UnaryOp._

  @inline private def unOp(op: UnaryOp.Op): GE = op.make(g)

  // unary ops
  def unary_-   : GE  = unOp(Neg       )
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
  def midicps   : GE  = unOp(Midicps   )
  def cpsmidi   : GE  = unOp(Cpsmidi   )
  def midiratio : GE  = unOp(Midiratio )
  def ratiomidi : GE  = unOp(Ratiomidi )
  def dbamp     : GE  = unOp(Dbamp     )
  def ampdb     : GE  = unOp(Ampdb     )
  def octcps    : GE  = unOp(Octcps    )
  def cpsoct    : GE  = unOp(Cpsoct    )
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
  // def linrand : GE           = UnOp.make( 'linrand, this )
  // def bilinrand : GE         = UnOp.make( 'bilinrand, this )
  // def sum3rand : GE          = UnOp.make( 'sum3rand, this )
//  def distort   : GE  = unOp(Distort   )
//  def softclip  : GE  = unOp(Softclip  )

  // def coin : GE              = UnOp.make( 'coin, this )
  // def even : GE              = UnOp.make( 'even, this )
  // def odd : GE               = UnOp.make( 'odd, this )
  // def rectWindow : GE        = UnOp.make( 'rectWindow, this )
  // def hanWindow : GE         = UnOp.make( 'hanWindow, this )
  // def welWindow : GE         = UnOp.make( 'sum3rand, this )
  // def triWindow : GE         = UnOp.make( 'triWindow, this )
//  def ramp      : GE  = unOp(Ramp      )
//  def scurve    : GE  = unOp(Scurve    )

  // def isPositive : GE        = UnOp.make( 'isPositive, this )
  // def isNegative : GE        = UnOp.make( 'isNegative, this )
  // def isStrictlyPositive : GE= UnOp.make( 'isStrictlyPositive, this )
  // def rho : GE               = UnOp.make( 'rho, this )
  // def theta : GE             = UnOp.make( 'theta, this )

  import BinaryOp._

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
//  def &       (b: GE): GE = binOp(BitAnd  , b)
//  def |       (b: GE): GE = binOp(BitOr   , b)
//  def ^       (b: GE): GE = binOp(BitXor  , b)
  // def lcm(b: GE): GE = ...
  // def gcd(b: GE): GE = ...

  def roundTo (b: GE): GE = binOp(RoundTo , b)
  def roundUpTo (b: GE): GE = binOp(RoundUpTo, b)
  def trunc   (b: GE): GE = binOp(Trunc   , b)
  def atan2   (b: GE): GE = binOp(Atan2   , b)
  def hypot   (b: GE): GE = binOp(Hypot   , b)
  def hypotx  (b: GE): GE = binOp(Hypotx  , b)

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
  def difsqr  (b: GE): GE = binOp(Difsqr  , b)
  def sumsqr  (b: GE): GE = binOp(Sumsqr  , b)
  def sqrsum  (b: GE): GE = binOp(Sqrsum  , b)
  def sqrdif  (b: GE): GE = binOp(Sqrdif  , b)
  def absdif  (b: GE): GE = binOp(Absdif  , b)
  def thresh  (b: GE): GE = binOp(Thresh  , b)
  def amclip  (b: GE): GE = binOp(Amclip  , b)
  def scaleneg(b: GE): GE = binOp(Scaleneg, b)
  def clip2   (b: GE): GE = binOp(Clip2   , b)
  def excess  (b: GE): GE = binOp(Excess  , b)
  def fold2   (b: GE): GE = binOp(Fold2   , b)
  def wrap2   (b: GE): GE = binOp(Wrap2   , b)
//  def firstarg(b: GE): GE = binOp(Firstarg, b)

  // def rrand(b: GE): GE    = ...
  // def exprrand(b: GE): GE = ...

//  def clip(low: GE, high: GE): GE = {
//    Clip(r, g, low, high)
//  }
//
//  def fold(low: GE, high: GE): GE = {
//    Fold(r, g, low, high)
//  }
//
//  def wrap(low: GE, high: GE): GE = {
//    Wrap(r, g, low, high)
//  }
//
//  def linlin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
//    LinLin(/* rate, */ g, inLow, inHigh, outLow, outHigh)
//  }
//
//  def linexp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
//    LinExp(g.rate, g, inLow, inHigh, outLow, outHigh) // should be highest rate of all inputs? XXX
//  }

  def explin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE =
    (g / inLow).log / (inHigh / inLow).log * (outHigh - outLow) + outLow

  def expexp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE =
    (outHigh / outLow).pow((g / inLow).log / (inHigh / inLow).log) * outLow

  def takeRight(len: GE): GE = TakeRight(in = g, len = len)

  def last: GE = takeRight(1)
}