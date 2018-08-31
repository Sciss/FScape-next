/*
 *  Hash.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FlowShape}
import de.sciss.fscape.stream.impl.{FilterChunkImpl, FilterIn1IImpl, FilterIn1LImpl, NodeImpl, StageImpl}

object Hash {
  def fromInt(in: OutI)(implicit b: Builder): OutI = {
    val stage0  = new StageInt
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  def fromLong(in: OutL)(implicit b: Builder): OutL = {
    val stage0  = new StageLong
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  def fromDouble(in: OutD)(implicit b: Builder): OutL = {
    val stage0  = new StageDouble
    val stage   = b.add(stage0)
    b.connect(in, stage.in)
    stage.out
  }

  private final val name = "Hash"

  private type Shape[BI >: Null <: BufLike, BO >: Null <: BufLike] = FlowShape[BI, BO]

  private final class StageInt(implicit ctrl: Control)
    extends StageImpl[Shape[BufI, BufI]](s"$name.Int") {

    val shape = new FlowShape(
      in  = InI (s"$name.in" ),
      out = OutI(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Hash.Shape[BufI, BufI]] = new IntLogic(shape)
  }

  private final class StageLong(implicit ctrl: Control)
    extends StageImpl[Shape[BufL, BufL]](s"$name.Long") {

    val shape = new FlowShape(
      in  = InL (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Hash.Shape[BufL, BufL]] = new LongLogic(shape)
  }

  private final class StageDouble(implicit ctrl: Control)
    extends StageImpl[Shape[BufD, BufL]](s"$name.Double") {

    val shape = new FlowShape(
      in  = InD (s"$name.in" ),
      out = OutL(s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Hash.Shape[BufD, BufL]] = new DoubleLogic(shape)
  }

  // using code from
  // https://github.com/tnm/murmurhash-java/blob/master/src/main/java/ie/ucd/murmur/MurmurHash.java
  // by Viliam Holub, Public Domain

  private final class IntLogic(shape: Hash.Shape[BufI, BufI])(implicit ctrl: Control)
    extends LogicBase[BufI, BufI](shape, "Int")
    with FilterIn1IImpl[BufI] {

    private[this] final val seed  = 0x9747b28c
    private[this] final val m     = 0x5bd1e995
    private[this] final val r     = 24

    private[this] var h = seed    // no prior known length

    // private[this] var length = 0

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val in      = bufIn0.buf
      val out     = bufOut0.buf
      while (inOffI < inStop) {
        var k = in(inOffI)
        k *= m
        k ^= k >>> r
        k *= m

        h *= m
        h ^= k

        out(outOffI) = h
        inOffI  += 1
        outOffI += 1
      }

      // length += chunk
    }
  }

  private final class LongLogic(shape: Hash.Shape[BufL, BufL])(implicit ctrl: Control)
    extends LogicBase[BufL, BufL](shape, "Long")
      with FilterIn1LImpl[BufL] {

    private[this] final val seed  = 0xe17a1465
    private[this] final val m     = 0xc6a4a7935bd1e995L
    private[this] final val r     = 47

    private[this] var h = seed & 0xFFFFFFFFL    // no prior known length

    // private[this] var length = 0

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val in      = bufIn0.buf
      val out     = bufOut0.buf
      while (inOffI < inStop) {
        var k = in(inOffI)
        k *= m
        k ^= k >>> r
        k *= m

        h ^= k
        h *= m

        out(outOffI) = h
        inOffI  += 1
        outOffI += 1
      }

      // length += chunk
    }
  }

  private final class DoubleLogic(shape: Hash.Shape[BufD, BufL])(implicit ctrl: Control)
    extends LogicBase[BufD, BufL](shape, "Double")
      with FilterIn1LImpl[BufD] {

    private[this] final val seed  = 0xe17a1465
    private[this] final val m     = 0xc6a4a7935bd1e995L
    private[this] final val r     = 47

    private[this] var h = seed & 0xFFFFFFFFL    // no prior known length

    // private[this] var length = 0

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      var inOffI  = inOff
      var outOffI = outOff
      val inStop  = inOffI + chunk
      val in      = bufIn0.buf
      val out     = bufOut0.buf
      while (inOffI < inStop) {
        var k = java.lang.Double.doubleToLongBits(in(inOffI))
        k *= m
        k ^= k >>> r
        k *= m

        h ^= k
        h *= m

        out(outOffI) = h
        inOffI  += 1
        outOffI += 1
      }

      // length += chunk
    }
  }

  private abstract class LogicBase[BI >: Null <: BufLike, BO >: Null <: BufLike](shape: Shape[BI, BO],
                                                                             tpe: String)
                                                                            (implicit ctrl: Control)
    extends NodeImpl[Shape[BI, BO]](s"$name.$tpe", shape)
      with FilterChunkImpl[BI, BO, Shape[BI, BO]]
}