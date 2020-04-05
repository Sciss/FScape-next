/*
 *  TakeRight.scala
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

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.graph.ConstantI
import de.sciss.fscape.stream.impl.deprecated.FilterIn2Impl
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}

object TakeRight {
  def last[A, E <: BufElem[A]](in: Outlet[E])
                              (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val length = ConstantI(1).toInt
    apply[A, E](in = in, length = length)
  }

  def apply[A, E <: BufElem[A]](in: Outlet[E], length: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(length, stage.in1)
    stage.out
  }

  private final val name = "TakeRight"

  private type Shp[E] = FanInShape2[E, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet [E](s"$name.in"    ),
      in1 = InI        (s"$name.length"),
      out = Outlet[E](s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
      with FilterIn2Impl[E, BufI, E] {

    private[this] var len     : Int       = _
    private[this] var bufWin  : Array[A]  = _     // circular
    private[this] var bufWritten = 0L

    private[this] var outOff              = 0
    private[this] var outRemain           = 0
    private[this] var outSent             = true

    private[this] var bufOff    : Int = _
    private[this] var bufRemain : Int = _

    private[this] var writeMode = false

    protected def allocOutBuf0(): E = tpe.allocBuf()

    def process(): Unit = {
      logStream(s"process() $this ${if (writeMode) "W" else "R"}")

      if (writeMode) tryWrite()
      else {
        if (canRead) {
          readIns()
          if (bufWin == null) {
            len    = math.max(1, bufIn1.buf(0))
            bufWin = tpe.newArray(len) // new Array[A](len)
          }
          copyInputToBuffer()
        }
        if (isClosed(in0) && !isAvailable(in0)) {
          bufRemain   = math.min(bufWritten, len).toInt
          bufOff      = (math.max(0L, bufWritten - len) % len).toInt
          writeMode   = true
          tryWrite()
        }
      }
    }

    private def copyInputToBuffer(): Unit = {
      val inRemain  = bufIn0.size
      val chunk     = math.min(inRemain, len)
      var inOff     = inRemain - chunk
      var bufOff    = ((bufWritten + inOff) % len).toInt
      val chunk1    = math.min(chunk, len - bufOff)
      if (chunk1 > 0) {
        // println(s"copy1($inOff / $inRemain -> $bufOff / $len -> $chunk1")
        System.arraycopy(bufIn0.buf, inOff, bufWin, bufOff, chunk1)
//        Util.copy       (bufIn0.buf, inOff, bufWin, bufOff, chunk1)
        bufOff = (bufOff + chunk1) % len
        inOff += chunk1
      }
      val chunk2 = chunk - chunk1
      if (chunk2 > 0) {
        // println(s"copy2($inOff / $inRemain -> $bufOff / $len -> $chunk2")
        System.arraycopy(bufIn0.buf, inOff, bufWin, bufOff, chunk2)
//        Util.copy       (bufIn0.buf, inOff, bufWin, bufOff, chunk2)
        // bufOff = (bufOff + chunk2) % len
        // inOff += chunk2
      }
      bufWritten += inRemain
    }

    protected def tryWrite(): Unit = {
      if (outSent) {
        bufOut0        = allocOutBuf0()
        outRemain     = bufOut0.size
        outOff        = 0
        outSent       = false
      }

      val chunk = math.min(bufRemain, outRemain)
      if (chunk > 0) {
        val chunk1  = math.min(len - bufOff, chunk)
        System.arraycopy(bufWin, bufOff, bufOut0.buf, outOff, chunk1)
//        Util.copy       (bufWin, bufOff, bufOut0.buf, outOff, chunk1)
        bufOff  = (bufOff + chunk1) % len
        outOff += chunk1
        val chunk2  = chunk - chunk1
        if (chunk2 > 0) {
          System.arraycopy(bufWin, bufOff, bufOut0.buf, outOff, chunk2)
//          Util.copy       (bufWin, bufOff, bufOut0.buf, outOff, chunk2)
          bufOff  = (bufOff + chunk2) % len
          outOff += chunk2
        }

        bufRemain -= chunk
        outRemain -= chunk
      }

      val flushOut = bufRemain == 0
      if (!outSent && (outRemain == 0 || flushOut) && isAvailable(out0)) {
        if (outOff > 0) {
          bufOut0.size = outOff
          push(out0, bufOut0)
        } else {
          bufOut0.release()
        }
        bufOut0     = null.asInstanceOf[E]
        outSent     = true
      }

      if (flushOut && outSent) {
        logStream(s"completeStage() $this")
        completeStage()
      }
    }
  }
}