/*
 *  SortWindow.scala
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

import java.util

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{DemandAuxInHandler, DemandFilterLogic, DemandInOutImpl, DemandProcessInHandler, DemandWindowedLogicOLD, NodeImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

object SortWindow {
  def apply[A, K >: Null <: BufElem[A],
  B, V >: Null <: BufElem[B]](keys  : Outlet[K],
                              values: Outlet[V], size: OutI)
                             (implicit b: Builder,
                              keyTpe  : StreamType[A, K],
                              valueTpe: StreamType[B, V]): Outlet[V] = {
    val stage0  = new Stage[A, K, B, V](b.layer)
    val stage   = b.add(stage0)
    b.connect(keys  , stage.in0)
    b.connect(values, stage.in1)
    b.connect(size  , stage.in2)
    stage.out
  }

  private final val name = "SortWindow"

  private type Shape[A, K >: Null <: BufElem[A], B, V >: Null <: BufElem[B]] =
    FanInShape3[K, V, BufI, V]

  private final class Stage[A, K >: Null <: BufElem[A],
  B, V >: Null <: BufElem[B]](layer: Layer)
                             (implicit ctrl: Control,
                              keyTpe: StreamType[A, K], valueTpe: StreamType[B, V])
    extends StageImpl[Shape[A, K, B, V]](name) {

    val shape = new FanInShape3(
      in0 = Inlet[K] (s"$name.keys"  ),
      in1 = Inlet[V] (s"$name.values"),
      in2 = InI      (s"$name.size"  ),
      out = Outlet[V](s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic[A, K, B, V](shape, layer)
  }

  private final class Logic[A, K >: Null <: BufElem[A],
  B, V >: Null <: BufElem[B]](shape: Shape[A, K, B, V], layer: Layer)
                             (implicit ctrl: Control, keyTpe: StreamType[A, K],
                              valueTpe: StreamType[B, V])
    extends NodeImpl(name, layer, shape)
      with DemandFilterLogic[K, Shape[A, K, B, V]]
      with DemandWindowedLogicOLD[ Shape[A, K, B, V]]
      with Out1LogicImpl    [V, Shape[A, K, B, V]]
      with DemandInOutImpl[     Shape[A, K, B, V]] {

    import keyTpe.{ordering => keyOrd}

    private[this] var winSize = 0
    private[this] var winBuf: Array[(A, B)] = _

    protected def allocOutBuf0(): V = valueTpe.allocBuf()

    protected     var bufIn0 : K    = _
    private[this] var bufIn1 : V    = _
    private[this] var bufIn2 : BufI = _
    protected     var bufOut0: V    = _

    protected     def in0: Inlet[K] = shape.in0

    private[this] var _mainCanRead  = false
    private[this] var _auxCanRead   = false
    private[this] var _mainInValid  = false
    private[this] var _auxInValid   = false
    private[this] var _inValid      = false

    protected def out0: Outlet[V] = shape.out

    def mainCanRead : Boolean = _mainCanRead
    def auxCanRead  : Boolean = _auxCanRead
    def mainInValid : Boolean = _mainInValid
    def auxInValid  : Boolean = _auxInValid
    def inValid     : Boolean = _inValid

    override protected def stopped(): Unit = {
      freeInputBuffers()
      freeOutputBuffers()
      winBuf = null
    }

    protected def readMainIns(): Int = {
      freeMainInBuffers()
      val sh        = shape
      bufIn0        = grab(sh.in0)
      bufIn0.assertAllocated()
      tryPull(sh.in0)

      bufIn1        = grab(sh.in1)
      bufIn1.assertAllocated()
      tryPull(sh.in1)

      if (!_mainInValid) {
        _mainInValid= true
        _inValid    = _auxInValid
      }

      _mainCanRead = false
      math.min(bufIn0.size, bufIn1.size)
    }

    protected def readAuxIns(): Int = {
      freeAuxInBuffers()
      val sh    = shape
      var sz    = 0

      if (isAvailable(sh.in2)) {
        bufIn2  = grab(sh.in2)
        sz      = math.max(sz, bufIn2.size)
        tryPull(sh.in2)
      }

      if (!_auxInValid) {
        _auxInValid = true
        _inValid    = _mainInValid
      }

      _auxCanRead = false
      sz
    }

    private def freeInputBuffers(): Unit = {
      freeMainInBuffers()
      freeAuxInBuffers()
    }

    private def freeMainInBuffers(): Unit = {
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }
    }

    private def freeAuxInBuffers(): Unit = {
      if (bufIn2 != null) {
        bufIn2.release()
        bufIn2 = null
      }
    }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    def updateMainCanRead(): Unit = {
      val sh = shape
      _mainCanRead = isAvailable(sh.in0) && isAvailable(sh.in1)
    }

    def updateAuxCanRead(): Unit = {
      val sh = shape
      _auxCanRead = (isClosed(sh.in2) && _auxInValid) || isAvailable(sh.in2)
    }

    new DemandProcessInHandler(shape.in0, this)
    new DemandProcessInHandler(shape.in1, this)
    new DemandAuxInHandler    (shape.in2, this)
    new ProcessOutHandlerImpl (shape.out, this)

    // highest priority = lowest keys
    private[this] object SortedKeys extends Ordering[(A, B)] {
      def compare(x: (A, B), y: (A, B)): Int = keyOrd.compare(x._1, y._1)
    }

    protected def startNextWindow(): Long = {
      val oldSize = winSize
      val inOff   = auxInOff
      if (bufIn2 != null && inOff < bufIn2.size) {
        winSize = math.max(1, bufIn2.buf(inOff))
      }
      if (winSize != oldSize) {
        winBuf = new Array[(A, B)](winSize) // valueTpe.newArray(winSize)
      }
      winSize
    }

    protected def canStartNextWindow: Boolean = auxInRemain > 0 || (auxInValid && isClosed(shape.in2))

    protected def copyInputToWindow(writeToWinOff: Long, chunk: Int): Unit = {
      // System.arraycopy(bufIn0.buf, mainInOff, winBuf, writeToWinOff.toInt, chunk)
      val b0 = bufIn0.buf
      val b1 = bufIn1.buf
      val b2 = winBuf
      var i  = mainInOff
      var j  = writeToWinOff.toInt
      val k  = i + chunk
      while (i < k) {
        val tup = (b0(i), b1(i))
        b2(j) = tup
        i += 1
        j += 1
      }
    }

    protected def processWindow(writeToWinOff: Long): Long = {
      util.Arrays.sort(winBuf, 0, writeToWinOff.toInt, SortedKeys)
      writeToWinOff
    }

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit = {
      val b0 = winBuf
      val b1 = bufOut0.buf
      var i  = readFromWinOff.toInt
      var j  = outOff
      val k  = i + chunk
      while (i < k) {
        val tup = b0(i)
        b1(j) = tup._2
        i += 1
        j += 1
      }
    }
  }
}