/*
 *  ValueSeq.scala
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

import akka.stream.{Attributes, SourceShape}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn0Impl, NodeImpl, StageImpl}

object ValueSeq {
  def int(elems: Array[Int])(implicit b: Builder): OutI = {
    val stage0  = new StageInt(elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def long(elems: Array[Long])(implicit b: Builder): OutL = {
    val stage0  = new StageLong(elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def double(elems: Array[Double])(implicit b: Builder): OutD = {
    val stage0  = new StageDouble(elems)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "ValueSeq"

  private type ShapeInt     = SourceShape[BufI]
  private type ShapeLong    = SourceShape[BufL]
  private type ShapeDouble  = SourceShape[BufD]

  private final class StageInt(elems: Array[Int])(implicit ctrl: Control) extends StageImpl[ShapeInt](name) {
    val shape = new SourceShape(
      out = OutI(s"$name.out")
    )

    def createLogic(attr: Attributes) = new LogicInt(elems, shape)
  }

  private final class StageLong(elems: Array[Long])(implicit ctrl: Control) extends StageImpl[ShapeLong](name) {
    val shape = new SourceShape(
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes) = new LogicLong(elems, shape)
  }

  private final class StageDouble(elems: Array[Double])(implicit ctrl: Control) extends StageImpl[ShapeDouble](name) {
    val shape = new SourceShape(
      out = OutD(s"$name.out")
    )

    def createLogic(attr: Attributes) = new LogicDouble(elems, shape)
  }

  private final class LogicInt(elems: Array[Int], shape: ShapeInt)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with GenChunkImpl[BufI, BufI, ShapeInt]
      with GenIn0Impl[BufI] {

    private[this] var index = 0

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      val buf   = bufOut0.buf
      var off   = outOff
      val stop  = off + chunk
      var i     = index
      while (off < stop) {
        buf(off) = elems(i)
        i = i + 1
        if (i == elems.length) i = 0
        off += 1
      }
      index = i
    }
  }

  private final class LogicLong(elems: Array[Long], shape: ShapeLong)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with GenChunkImpl[BufL, BufL, ShapeLong]
      with GenIn0Impl[BufL] {

    private[this] var index = 0

    protected def allocOutBuf0(): BufL = ctrl.borrowBufL()

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      val buf   = bufOut0.buf
      var off   = outOff
      val stop  = off + chunk
      var i     = index
      while (off < stop) {
        buf(off) = elems(i)
        i = i + 1
        if (i == elems.length) i = 0
        off += 1
      }
      index = i
    }
  }

  private final class LogicDouble(elems: Array[Double], shape: ShapeDouble)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with GenChunkImpl[BufD, BufD, ShapeDouble]
      with GenIn0Impl[BufD] {

    private[this] var index = 0

    protected def allocOutBuf0(): BufD = ctrl.borrowBufD()

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      val buf   = bufOut0.buf
      var off   = outOff
      val stop  = off + chunk
      var i     = index
      while (off < stop) {
        buf(off) = elems(i)
        i = i + 1
        if (i == elems.length) i = 0
        off += 1
      }
      index = i
    }
  }
}