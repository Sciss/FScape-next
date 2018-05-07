/*
 *  Fold.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{AbstractClipFoldWrapD, AbstractClipFoldWrapI, AbstractClipFoldWrapL, StageImpl}
import de.sciss.numbers.{DoubleFunctions, IntFunctions}

object Fold {
  def int(in: OutI, lo: OutI, hi: OutI)(implicit b: Builder): OutI = {
    val stage0  = new StageInt
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(lo, stage.in1)
    b.connect(hi, stage.in2)
    stage.out
  }

  def long(in: OutL, lo: OutL, hi: OutL)(implicit b: Builder): OutL = {
    val stage0  = new StageLong
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(lo, stage.in1)
    b.connect(hi, stage.in2)
    stage.out
  }

  def double(in: OutD, lo: OutD, hi: OutD)(implicit b: Builder): OutD = {
    val stage0  = new StageDouble
    val stage   = b.add(stage0)
    b.connect(in, stage.in0)
    b.connect(lo, stage.in1)
    b.connect(hi, stage.in2)
    stage.out
  }

  private final val name = "Fold"

  private type ShapeInt     = FanInShape3[BufI, BufI, BufI, BufI]
  private type ShapeLong    = FanInShape3[BufL, BufL, BufL, BufL]
  private type ShapeDouble  = FanInShape3[BufD, BufD, BufD, BufD]

  private final class StageInt(implicit ctrl: Control) extends StageImpl[ShapeInt](name) {
    val shape = new FanInShape3(
      in0 = InI (s"$name.in"),
      in1 = InI (s"$name.lo"),
      in2 = InI (s"$name.hi"),
      out = OutI(s"$name.out")
    )

    def createLogic(attr: Attributes) = new LogicInt(shape)
  }

  private final class StageLong(implicit ctrl: Control) extends StageImpl[ShapeLong](name) {
    val shape = new FanInShape3(
      in0 = InL (s"$name.in"),
      in1 = InL (s"$name.lo"),
      in2 = InL (s"$name.hi"),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes) = new LogicLong(shape)
  }

  private final class StageDouble(implicit ctrl: Control) extends StageImpl[ShapeDouble](name) {
    val shape = new FanInShape3(
      in0 = InD (s"$name.in"),
      in1 = InD (s"$name.lo"),
      in2 = InD (s"$name.hi"),
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new LogicDouble(shape)
  }

  private final class LogicInt(shape: ShapeInt)(implicit ctrl: Control)
    extends AbstractClipFoldWrapI(name, shape) {

    protected def op(inVal: Int, loVal: Int, hiVal: Int): Int =
      IntFunctions.fold(inVal, loVal, hiVal)
  }

  private final class LogicLong(shape: ShapeLong)(implicit ctrl: Control)
    extends AbstractClipFoldWrapL(name, shape) {

    protected def op(inVal: Long, loVal: Long, hiVal: Long): Long = {
      // cf. Numbers issue #6
      val b   = hiVal - loVal
      val b2  = b + b
      val c0  = mod(inVal - loVal, b2)
      val c   = if (c0 > b) b2 - c0 else c0
      c + loVal
    }

    // handles negative numbers differently than a % b
    @inline private def mod(a: Long, b: Long): Long = if (b == 0) 0L else {
      var in = a
      if (a >= b) {
        in -= b
        if (in < b) return in
      } else if (a < 0) {
        in += b
        if (in >= 0) return in
      } else return in

      val c = in % b
      if (c < 0) c + b else c
    }
  }

  private final class LogicDouble(shape: ShapeDouble)(implicit ctrl: Control)
    extends AbstractClipFoldWrapD(name, shape) {

    protected def op(inVal: Double, loVal: Double, hiVal: Double): Double =
      DoubleFunctions.fold(inVal, loVal, hiVal)
  }
}