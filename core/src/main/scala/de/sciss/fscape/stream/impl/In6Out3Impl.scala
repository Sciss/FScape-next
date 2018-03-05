/*
 *  In6Out3Impl.scala
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
package impl

import akka.stream.Inlet
import akka.stream.stage.GraphStageLogic

// XXX TODO --- we could easily split now between input and output trait
// and would reduce the number of implementation traits necessary
trait In6Out3Impl[In0 >: Null <: BufLike, In1 >: Null <: BufLike, In2 >: Null <: BufLike,
In3 >: Null <: BufLike, In4 >: Null <: BufLike, In5 >: Null <: BufLike, Out0 >: Null <: BufLike, 
Out1 >: Null <: BufLike, Out2 >: Null <: BufLike]
  extends FullInOutImpl[In6Out3Shape[In0, In1, In2, In3, In4, In5, Out0, Out1, Out2]] {
  _: GraphStageLogic with Node =>

  // ---- impl ----

  // XXX TODO -- should be read-only for sub-classes
  protected final var bufIn0 : In0  = _
  protected final var bufIn1 : In1  = _
  protected final var bufIn2 : In2  = _
  protected final var bufIn3 : In3  = _
  protected final var bufIn4 : In4  = _
  protected final var bufIn5 : In5  = _
  protected final var bufOut0: Out0 = _
  protected final var bufOut1: Out1 = _
  protected final var bufOut2: Out2 = _

  protected final def in0: Inlet[In0] = shape.in0
  protected final def in1: Inlet[In1] = shape.in1
  protected final def in2: Inlet[In2] = shape.in2
  protected final def in3: Inlet[In3] = shape.in3
  protected final def in4: Inlet[In4] = shape.in4
  protected final def in5: Inlet[In5] = shape.in5

  private[this] final var _canRead  = false
  private[this] final var _canWrite = false
  private[this] final var _inValid  = false

  final def canRead : Boolean = _canRead
  final def canWrite: Boolean = _canWrite
  final def inValid : Boolean = _inValid

  protected def allocOutBuf0(): Out0
  protected def allocOutBuf1(): Out1
  protected def allocOutBuf2(): Out2

  override def preStart(): Unit =
    shape.inlets.foreach(pull(_))

  override protected def stopped(): Unit = {
    freeInputBuffers()
    freeOutputBuffers()
  }

  protected final def readIns(): Int = {
    freeInputBuffers()
    val sh    = shape
    bufIn0    = grab(sh.in0)
    bufIn0.assertAllocated()
    tryPull(sh.in0)

    if (isAvailable(sh.in1)) {
      bufIn1 = grab(sh.in1)
      tryPull(sh.in1)
    }
    if (isAvailable(sh.in2)) {
      bufIn2 = grab(sh.in2)
      tryPull(sh.in2)
    }
    if (isAvailable(sh.in3)) {
      bufIn3 = grab(sh.in3)
      tryPull(sh.in3)
    }
    if (isAvailable(sh.in4)) {
      bufIn4 = grab(sh.in4)
      tryPull(sh.in4)
    }
    if (isAvailable(sh.in5)) {
      bufIn5 = grab(sh.in5)
      tryPull(sh.in5)
    }

    _inValid = true
    _canRead = false
    bufIn0.size
  }

  protected final def freeInputBuffers(): Unit = {
    if (bufIn0 != null) {
      bufIn0.release()
      bufIn0 = null
    }
    if (bufIn1 != null) {
      bufIn1.release()
      bufIn1 = null
    }
    if (bufIn2 != null) {
      bufIn2.release()
      bufIn2 = null
    }
    if (bufIn3 != null) {
      bufIn3.release()
      bufIn3 = null
    }
    if (bufIn4 != null) {
      bufIn4.release()
      bufIn4 = null
    }
    if (bufIn5 != null) {
      bufIn5.release()
      bufIn5 = null
    }
  }

  protected final def freeOutputBuffers(): Unit = {
    if (bufOut0 != null) {
      bufOut0.release()
      bufOut0 = null
    }
    if (bufOut1 != null) {
      bufOut1.release()
      bufOut1 = null
    }
    if (bufOut2 != null) {
      bufOut2.release()
      bufOut2 = null
    }
  }

  final def updateCanRead(): Unit = {
    val sh = shape
    _canRead = isAvailable(sh.in0) &&
      ((isClosed(sh.in1) && _inValid) || isAvailable(sh.in1)) &&
      ((isClosed(sh.in2) && _inValid) || isAvailable(sh.in2)) &&
      ((isClosed(sh.in3) && _inValid) || isAvailable(sh.in3)) &&
      ((isClosed(sh.in4) && _inValid) || isAvailable(sh.in4)) &&
      ((isClosed(sh.in5) && _inValid) || isAvailable(sh.in5))
  }

  final def updateCanWrite(): Unit = {
    val sh = shape
    _canWrite = isAvailable(sh.out0) && isAvailable(sh.out1) && isAvailable(sh.out2)
  }

  protected final def writeOuts(outOff: Int): Unit = {
    if (outOff > 0) {
      bufOut0.size = outOff
      bufOut1.size = outOff
      bufOut2.size = outOff
      push(shape.out0, bufOut0)
      push(shape.out1, bufOut1)
      push(shape.out2, bufOut2)
    } else {
      bufOut0.release()
      bufOut1.release()
      bufOut2.release()
    }
    bufOut0 = null
    bufOut1 = null
    bufOut2 = null
    _canWrite = false
  }

  protected final def allocOutputBuffers(): Int = {
    bufOut0 = allocOutBuf0()
    bufOut1 = allocOutBuf1()
    bufOut2 = allocOutBuf2()
    bufOut0.size
  }

  new ProcessInHandlerImpl (shape.in0 , this)
  new AuxInHandlerImpl     (shape.in1 , this)
  new AuxInHandlerImpl     (shape.in2 , this)
  new AuxInHandlerImpl     (shape.in3 , this)
  new AuxInHandlerImpl     (shape.in4 , this)
  new AuxInHandlerImpl     (shape.in5 , this)
  new ProcessOutHandlerImpl(shape.out0, this)
  new ProcessOutHandlerImpl(shape.out1, this)
  new ProcessOutHandlerImpl(shape.out2, this)
}