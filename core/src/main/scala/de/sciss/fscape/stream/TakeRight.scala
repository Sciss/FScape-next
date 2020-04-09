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
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.math.{max, min}

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
      in1 = InI      (s"$name.length"),
      out = Outlet[E](s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn   = Handlers.InMain [A, E](this, shape.in0)
    private[this] val hSize = Handlers.InIAux       (this, shape.in1)(math.max(0, _))
    private[this] val hOut  = Handlers.OutMain[A, E](this, shape.out)

    private[this] var len     : Int       = _
    private[this] var bufSize : Int       = _
    private[this] var bufWin  : Array[A]  = _     // circular
    private[this] var bufWritten = 0L

    private[this] var bufOff    : Int = 0
    private[this] var bufRemain : Int = _

    private[this] var state = 0 // 0 get length, 1 read into buffer, 2 write from buffer

    override protected def stopped(): Unit = {
      super.stopped()
      bufWin = null
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (state == 0) {
        completeStage()
      } else {
        assert (state == 1)
        process()
      }

    def process(): Unit = {
      logStream(s"process() $this state = $state")

      if (state == 0) {
        if (!hSize.hasNext) return
        len       = hSize.next()
        bufSize   = max(len, ctrl.blockSize)
        bufWin    = tpe.newArray(bufSize)
        state     = 1
      }
      if (state == 1) {
        readIntoWindow()
        if (!hIn.isDone) return
        bufRemain = min(bufWritten, len).toInt
        bufOff    = (max(0L, bufWritten - len) % bufSize).toInt
        state     = 2
      }

      writeFromWindow()
      if (bufRemain == 0) {
        if (hOut.flush()) completeStage()
      }
    }

    @tailrec
    private def readIntoWindow(): Unit = {
      val rem     = hIn.available
      if (rem == 0) return

      val chunk   = min(rem, bufSize) // maximum one full buffer, as we loop the method
      val chunk1  = min(chunk, bufSize - bufOff)
      if (chunk1 > 0) {
        hIn.nextN(bufWin, bufOff, chunk1)
        bufOff = (bufOff + chunk1) % bufSize
      }
      val chunk2  = chunk - chunk1
      if (chunk2 > 0) {
        hIn.nextN(bufWin, bufOff, chunk2)
        bufOff = (bufOff + chunk2) % bufSize
      }
      bufWritten += rem
      readIntoWindow()
    }

    private def writeFromWindow(): Unit = {
      val rem     = min(bufRemain, hOut.available)
      if (rem == 0) return

      val chunk1  = min(rem, bufSize - bufOff)
      hOut.nextN(bufWin, bufOff, chunk1)
      bufOff      = (bufOff + chunk1) % bufSize
      val chunk2  = rem - chunk1
      if (chunk2 > 0) {
        hOut.nextN(bufWin, bufOff, chunk2)
        bufOff = (bufOff + chunk2) % bufSize
      }

      bufRemain -= rem
    }
  }
}