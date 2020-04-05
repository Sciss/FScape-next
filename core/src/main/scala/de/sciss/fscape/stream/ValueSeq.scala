/*
 *  ValueSeq.scala
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
package stream

import akka.stream.{Attributes, Outlet, SourceShape}
import de.sciss.fscape.stream.impl.deprecated.{ChunkImpl, GenIn0Impl}
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object ValueSeq {
  def int(elems: Array[Int])(implicit b: Builder): OutI = {
    val stage0  = new Stage[Int, BufI](b.layer, elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def long(elems: Array[Long])(implicit b: Builder): OutL = {
    val stage0  = new Stage[Long, BufL](b.layer, elems)
    val stage   = b.add(stage0)
    stage.out
  }

  def double(elems: Array[Double])(implicit b: Builder): OutD = {
    val stage0  = new Stage[Double, BufD](b.layer, elems)
    val stage   = b.add(stage0)
    stage.out
  }

  private final val name = "ValueSeq"

  private type Shp[E] = SourceShape[E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer, elems: Array[A])
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new SourceShape(
      out = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer, elems)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer, elems: Array[A])
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with ChunkImpl[Shp[E]]
      with GenIn0Impl[E] {

    private[this] var index = 0

    protected def allocOutBuf0(): E = tpe.allocBuf()

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