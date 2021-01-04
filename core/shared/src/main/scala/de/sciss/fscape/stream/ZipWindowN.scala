/*
 *  ZipWindowN.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InMain, OutMain}
import de.sciss.fscape.stream.impl.logic.WindowedMultiInOut
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.collection.immutable.{Seq => ISeq}
import scala.math.{max, min}

/** Zips a number of signals into one output based on a window length. */
object ZipWindowN {
  /**
    * @param in         the signals to zip
    * @param size       the window size. this is clipped to be `&lt;= 1`
    */
  def apply[A, E <: BufElem[A]](in: ISeq[Outlet[E]], size: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](layer = b.layer, numInputs = in.size)
    val stage   = b.add(stage0)
    (in zip stage.inputs).foreach { case (output, input) =>
      b.connect(output, input)
    }
    b.connect(size, stage.size)
    stage.out
  }

  private final val name = "ZipWindowN"

  private final case class Shp[E](inputs: ISeq[Inlet[E]], size: InI, out: Outlet[E]) extends akka.stream.Shape {
    val inlets : ISeq[Inlet [_]] = inputs :+ size
    val outlets: ISeq[Outlet[_]] = Vector(out)

    override def deepCopy(): Shp[E] =
      Shp(inputs.map(_.carbonCopy()), size.carbonCopy(), out.carbonCopy())
  }

  private final class Stage[A, E <: BufElem[A]](layer: Layer, numInputs: Int)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = Shp(
      inputs  = Vector.tabulate(numInputs)(idx => Inlet[E](s"$name.in$idx")),
      size    = InI       (s"$name.size"),
      out     = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) with WindowedMultiInOut {

    private[this] val numInputs         = shape.inputs.size

    private[this] val hIns  : Array[InMain[A, E]]  =
      Array.tabulate(numInputs)(ch => InMain[A, E](this, shape.inputs(ch)))

    private[this] val hSize : InIAux        = InIAux       (this, shape.size)(max(1, _))
    private[this] val hOut  : OutMain[A, E] = OutMain[A, E](this, shape.out)

    private[this] var winBuf: Array[A] = _

    private[this] var size      : Int = -1
    private[this] var sizeOut   : Int = _

    override protected def launch(): Unit =
      if (numInputs == 0) completeStage() else super.launch()


    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext
      if (ok) {
        val _size = hSize.next()
        if (size != _size) {
          size    = _size
          sizeOut = _size * numInputs
          winBuf  = tpe.newArray(sizeOut)
        }
      }
      ok
    }

    override protected def stopped(): Unit = {
      super.stopped()
      winBuf = null
    }

    protected def readWinSize : Long = size
    protected def writeWinSize: Long = sizeOut

    protected def readIntoWindow(chunk: Int): Unit = {
      var chOff = readOff.toInt
      val sz    = size
      val a     = hIns
      val b     = winBuf
      var ch = 0
      while (ch < numInputs) {
        a(ch).nextN(b, chOff, chunk)
        ch   += 1
        chOff += sz
      }
    }

    protected def writeFromWindow(n: Int): Unit = {
      hOut.nextN(winBuf, writeOff.toInt, n)
    }

    protected def mainInAvailable: Int = {
      val a   = hIns
      var res = Int.MaxValue
      var ch  = 0
      while (ch < numInputs) {
        res = min(res, a(ch).available)
        ch += 1
      }
      res
    }

    protected def outAvailable: Int = hOut.available

    protected def mainInDone: Boolean = {
      val a = hIns
      var ch = 0
      while (ch < numInputs) {
        if (a(ch).isDone) return true
        ch += 1
      }
      false
    }

    protected def isHotIn(inlet: Inlet[_]): Boolean = true

    protected def flushOut(): Boolean = hOut.flush()

    protected def outDone: Boolean = hOut.isDone

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)

    protected def processWindow(): Unit = {
      val offI  = readOff.toInt
      val sz    = size
      if (offI < sz) {
        val n     = sz - offI
        var ch    = 0
        var chOff = offI
        while (ch < numInputs) {
          tpe.clear(winBuf, chOff, n)
          ch    += 1
          chOff += sz
        }
      }
    }
  }
}