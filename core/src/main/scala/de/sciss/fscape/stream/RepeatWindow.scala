/*
 *  RepeatWindowNew.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.stage.InHandler
import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{NodeImpl, Out1LogicImpl, ProcessOutHandlerImpl, StageImpl}

import scala.annotation.tailrec

/** Repeats contents of windowed input.
  */
object RepeatWindow {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param num    the number of times each window is repeated
    */
  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], size: OutI, num: OutL)
                                       (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(num , stage.in2)
    stage.out
  }

  private final val name = "RepeatWindow"

  private type Shape[E] = FanInShape3[E, BufI, BufL, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shape[E]](name) {

    val shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"   ),
      in1 = InI       (s"$name.size" ),
      in2 = InL       (s"$name.num"  ),
      out = Outlet[E] (s"$name.out"  )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[E], layer: Layer)
                                                       (implicit ctrl: Control, tpe: StreamType[A, E])
    extends NodeImpl(name, layer, shape)
    with Out1LogicImpl[E, Shape[E]] {

    private[this] var winBuf : Array[A] = _

    protected var inRemain: Int = 0

    private[this] var num       : Long  = -1
    private[this] var winSize   : Int   = -1
    private[this] var readOff   : Int   = 0
    private[this] var writeOff  : Long  = 0
    private[this] var writeSize : Long  = 0

    private[this] var needsWinSize  = true
    private[this] var needsNum      = true

    private[this] var inOff0    : Int   = 0
    private[this] var inOff1    : Int   = 0
    private[this] var inOff2    : Int   = 0
    private[this] var outOff0   : Int   = 0

    private[this] var stage = 0
    private[this] var inputDone = false

    private[this] var bufIn0 : E  = _
    private[this] var bufIn1 : BufI = _
    private[this] var bufIn2 : BufL = _

    protected     var bufOut0: E  = _

    protected def allocOutBuf0(): E = tpe.allocBuf()

    private final class _InHandlerImpl[B](in: Inlet[B])(isValid: => Boolean) extends InHandler {
      def onPush(): Unit = {
        logStream(s"onPush($in)")
        process()
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish($in)")
        if (isValid) {
          process()
        } else if (!isInAvailable(in)) {
          super.onUpstreamFinish()
        }
      }

      setInHandler(in, this)
    }

    new _InHandlerImpl(shape.in0)(true)
    new _InHandlerImpl(shape.in1)(winSize >= 0)
    new _InHandlerImpl(shape.in2)(num     >= 0)
    new ProcessOutHandlerImpl(shape.out, this)

    def inValid: Boolean = winSize >= 0 && num >= 0

    protected def out0: Outlet[E] = shape.out

    private def freeBufIn0(): Unit =
      if (bufIn0 != null) {
        bufIn0.release()
        bufIn0 = null
      }

    private def freeBufIn1(): Unit =
      if (bufIn1 != null) {
        bufIn1.release()
        bufIn1 = null
      }

    private def freeBufIn2(): Unit =
      if (bufIn2 != null) {
        bufIn2.release()
        bufIn2 = null
      }

    override protected def stopped(): Unit = {
      super.stopped()
      freeInputBuffers()
      freeOutputBuffers()
      winBuf = null
    }

    private def freeInputBuffers(): Unit = {
      freeBufIn0()
      freeBufIn1()
      freeBufIn2()
    }

    protected def freeOutputBuffers(): Unit =
      if (bufOut0 != null) {
        bufOut0.release()
        bufOut0 = null
      }

    @tailrec
    def process(): Unit = {
      var stateChange = false

      if (stage == 0) {
        if (needsWinSize) {
          if (bufIn1 != null && inOff1 < bufIn1.size) {
            val oldSize = winSize
            winSize = math.max(1, bufIn1.buf(inOff1))
            if (winSize != oldSize) {
              winBuf = tpe.newArray(winSize) // new Array[Double](winSize)
            }
            inOff1       += 1
            needsWinSize  = false
            stateChange   = true
          } else if (isAvailable(shape.in1)) {
            freeBufIn1()
            bufIn1  = grab(shape.in1)
            inOff1  = 0
            tryPull(shape.in1)
            stateChange = true
          } else if (isClosed(shape.in1) && winSize >= 0) {
            needsWinSize  = false
            stateChange   = true
          }
        }

        if (needsNum) {
          if (bufIn2 != null && inOff2 < bufIn2.size) {
            num           = math.max(1, bufIn2.buf(inOff2))
            inOff2       += 1
            needsNum      = false
            stateChange   = true
          } else if (isAvailable(shape.in2)) {
            freeBufIn2()
            bufIn2    = grab(shape.in2)
            inOff2    = 0
            tryPull(shape.in2)
            stateChange = true
          } else if (isClosed(shape.in2) && num >= 0) {
            needsNum    = false
            stateChange = true
          }
        }

        if (!needsWinSize && !needsNum) {
          readOff     = 0
          stage       = 1
          stateChange = true
        }
      }

      if (stage == 1) {
        if (readOff < winSize) {
          if (bufIn0 != null && inRemain > 0) {
            val chunk = math.min(winSize - readOff, inRemain)
//            Util.copy(bufIn0.buf, inOff0, winBuf, readOff, chunk)
            System.arraycopy(bufIn0.buf, inOff0, winBuf, readOff, chunk)
            inOff0     += chunk
            inRemain   -= chunk
            readOff    += chunk
            stateChange = true
          } else if (isAvailable(shape.in0)) {
            freeBufIn0()
            bufIn0    = grab(shape.in0)
            inOff0    = 0
            inRemain  = bufIn0.size
            tryPull(shape.in0)
            stateChange = true
          } else if (isClosed(shape.in0)) {
            if (readOff > 0) {
              val chunk = winSize - readOff
              // Util.clear(winBuf, readOff, chunk)
              tpe.clear(winBuf, readOff, chunk)
              readOff   = winSize
            } else {
              winSize   = 0
            }
            inputDone   = true
            stateChange = true
          }
        }

        if (readOff == winSize) {
          writeOff    = 0
          stage       = 2
          writeSize   = num * winSize
          stateChange = true
        }
      }

      if (stage == 2) {
        if (bufOut0 == null) {
          bufOut0 = allocOutBuf0()
          outOff0 = 0
        }

        if (writeOff < writeSize) {
          if (outOff0 < bufOut0.size) {
            val chunk = math.min(writeSize - writeOff, bufOut0.size - outOff0).toInt
            var rem   = chunk
            while (rem > 0) {
              val i     = (writeOff % winSize).toInt
              val j     = math.min(rem, winSize - i)
              // Util.copy(winBuf, i, bufOut0.buf, outOff0, j)
              System.arraycopy(winBuf, i, bufOut0.buf, outOff0, j)
              outOff0  += j
              writeOff += j
              rem      -= j
            }
            stateChange = true
          }
        }

        if (outOff0 == bufOut0.size && canWrite) {
          writeOuts(outOff0)
          stateChange = true
        }

        if (writeOff == writeSize) {
          if (inputDone) {
            if (isAvailable(shape.out)) {
              writeOuts(outOff0)
              completeStage()
            }
          }
          else {
            stage         = 0
            needsWinSize  = true
            needsNum      = true
            stateChange   = true
          }
        }
      }

      if (stateChange) process()
    }
  }
}