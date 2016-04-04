package de.sciss.fscape.ugen

import de.sciss.fscape.Signal

case class ConstantInt   (i: Int)    extends Signal
case class ConstantLong  (n: Long)   extends Signal
case class ConstantDouble(d: Double) extends Signal
