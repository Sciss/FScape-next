/*
 *  UnzipWindow.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InMain, OutMain}
import de.sciss.fscape.stream.impl.logic.WindowedMultiInOut
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
import scala.collection.{Seq => SSeq}
import scala.math.{max, min}

/** Unzips a signal into two based on a window length. */
object UnzipWindow {
  /**
    * @param in     the signal to unzip
    * @param size   the window size. this is clipped to be `&lt;= 1`
    */
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): (Outlet[E], Outlet[E]) = {
    val SSeq(out0, out1) = UnzipWindowN[A, E](2, in = in, size = size)
    (out0, out1)
  }
}

/** Unzips a signal into a given number of outputs based on a window length. */
object UnzipWindowN {
  /**
    * @param numOutputs the number of outputs to de-interleave the input into
    * @param in         the signal to unzip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply[A, E <: BufElem[A]](numOutputs: Int, in: Outlet[E], size: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Vec[Outlet[E]] = {
    val stage0  = new Stage[A, E](layer = b.layer, numOutputs = numOutputs)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)

    stage.outlets.toIndexedSeq
  }

  private final val name = "UnzipWindowN"

  private final case class Shp[E](in0: Inlet[E], in1: InI, outlets: ISeq[Outlet[E]]) extends akka.stream.Shape {
    val inlets: ISeq[Inlet[_]] = Vector(in0, in1)

    override def deepCopy(): Shp[E] =
      Shp(in0.carbonCopy(), in1.carbonCopy(), outlets.map(_.carbonCopy()))
  }

  private final class Stage[A, E <: BufElem[A]](layer: Layer, numOutputs: Int)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = Shp(
      in0     = Inlet[E](s"$name.in"),
      in1     = InI     (s"$name.size"),
      outlets = Vector.tabulate(numOutputs)(idx => Outlet[E](s"$name.out$idx"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) with WindowedMultiInOut {

    private[this] val numOutputs = shape.outlets.size

    private[this] val hIn   : InMain[A, E]  = InMain[A, E](this, shape.in0)
    private[this] val hSize : InIAux        = InIAux      (this, shape.in1)(max(1, _))

    private[this] val hOuts : Array[OutMain[A, E]]  =
      Array.tabulate(numOutputs)(ch => OutMain[A, E](this, shape.outlets(ch)))

    private[this] var winBuf: Array[A] = _

    private[this] var size      : Int = -1
    private[this] var sizeIn    : Int = _

    override protected def launch(): Unit =
      if (numOutputs == 0) completeStage() else super.launch()

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext
      if (ok) {
        val _size = hSize.next()
        if (size != _size) {
          size    = _size
          sizeIn  = _size * numOutputs
          winBuf  = tpe.newArray(sizeIn)
        }
      }
      ok
    }

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def readWinSize : Long = sizeIn
    protected def writeWinSize: Long = readOff / numOutputs // size

    protected def readIntoWindow(n: Int): Unit = {
      hIn.nextN(winBuf, readOff.toInt, n)
    }

    protected def writeFromWindow(chunk: Int): Unit = {
      var chOff = writeOff.toInt
      val sz    = size
      val a     = hOuts
      val b     = winBuf
      var ch = 0
      while (ch < numOutputs) {
        val out = a(ch)
        if (!out.isDone) out.nextN(b, chOff, chunk)
        ch   += 1
        chOff += sz
      }
    }

    protected def mainInAvailable: Int = hIn.available

    protected def outAvailable: Int = {
      val a   = hOuts
      var res = Int.MaxValue
      var ch  = 0
      while (ch < numOutputs) {
        val out = a(ch)
        if (!out.isDone) res = min(res, out.available)
        ch += 1
      }
      res
    }

    protected def mainInDone: Boolean = hIn.isDone

    protected def isHotIn(inlet: Inlet[_]): Boolean = true

    protected def flushOut(): Boolean = {
      val a   = hOuts
      var res = true
      var ch  = 0
      while (ch < numOutputs) {
        val out = a(ch)
        if (!out.isDone) res &= out.flush()
        ch += 1
      }
      res
    }

    protected def outDone: Boolean = {
      val a   = hOuts
      var ch  = 0
      while (ch < numOutputs) {
        val out = a(ch)
        if (!out.isDone) return false
        ch += 1
      }
      true
    }

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)

    protected def processWindow(): Unit = ()
  }
}