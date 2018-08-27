/*
 *  ValueSeq.scala
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

import akka.stream.{Attributes, Outlet, SourceShape}
import de.sciss.fscape.stream.impl.{ChunkImpl, GenIn0Impl, NodeImpl, StageImpl}

object ValueSeq {
  def int(elems: Array[Int])(implicit b: Builder): OutI = {
    val stage0  = new Stage[Int, BufI](elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def long(elems: Array[Long])(implicit b: Builder): OutL = {
    val stage0  = new Stage[Long, BufL](elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def double(elems: Array[Double])(implicit b: Builder): OutD = {
    val stage0  = new Stage[Double, BufD](elems)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "ValueSeq"

  private type Shape[A, BufA <: BufLike { type Elem = A }] = SourceShape[BufA]

  private final class Stage[A, BufA >: Null <: BufLike { type Elem = A }](elems: Array[A])
                                                                  (implicit ctrl: Control, aTpe: StreamType[A, BufA])
    extends StageImpl[Shape[A, BufA]](name) {

    val shape = new SourceShape(
      out = Outlet[BufA](s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic[A, BufA](elems, shape)
  }

  private final class Logic[A, BufA >: Null <: BufLike { type Elem = A }](elems: Array[A], shape: Shape[A, BufA])
                                                                 (implicit ctrl: Control, aTpe: StreamType[A, BufA])
    extends NodeImpl(name, shape)
      with ChunkImpl[Shape[A, BufA]]
      with GenIn0Impl[BufA] {

    private[this] var index = 0

    protected def allocOutBuf0(): BufA = aTpe.allocBuf()

    protected def shouldComplete(): Boolean = index == elems.length

    protected def processChunk(): Boolean = {
      // XXX TODO --- why do we have to deal with `inRemain`?
      val chunk = math.min(elems.length - index, math.min(inRemain, outRemain))
      val res   = chunk > 0
      if (res) {
        processChunk(inOff = inOff, outOff = outOff, chunk = chunk)
        inOff       += chunk
        inRemain    -= chunk
        outOff      += chunk
        outRemain   -= chunk
//      } else if (shouldComplete()) {
//        completeStage()
      }
      res
    }

    private def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      val buf   = bufOut0.buf
      var off   = outOff
      val stop  = off + chunk
      var i     = index
      while (off < stop) {
        buf(off) = elems(i)
        i += 1
//        if (i == elems.length) i = 0
        off += 1
      }
      index = i
    }
  }
}