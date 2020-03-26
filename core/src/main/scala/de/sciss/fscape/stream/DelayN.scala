/*
 *  DelayN.scala
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

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{Handlers, StageImpl}

object DelayN {
  // XXX TODO remove overloaded method in next major version
  def apply(in: OutD, maxLength: OutI, length: OutI)(implicit b: Builder): OutD =
    apply[Double, BufD](in, maxLength, length)

  def apply[A, E >: Null <: BufElem[A]](in: Outlet[E], maxLength: OutI, length: OutI)
                                       (implicit b: Builder, aTpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(maxLength , stage.in1)
    b.connect(length    , stage.in2)
    stage.out
  }

  private final val name = "DelayN"

  private type Shape[A, E >: Null <: BufElem[A]] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E >: Null <: BufElem[A]](layer: Layer)(implicit ctrl: Control, aTpe: StreamType[A, E])
    extends StageImpl[Shape[A, E]](name) {

    val shape = new FanInShape3(
      in0 = Inlet[E]  (s"$name.in"        ),
      in1 = InI       (s"$name.maxLength" ),
      in2 = InI       (s"$name.length"    ),
      out = Outlet[E] (s"$name.out"       )
    )

    def createLogic(attr: Attributes) = new Logic[A, E](shape, layer)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], layer: Layer)
                                                       (implicit ctrl: Control, aTpe: StreamType[A, E])
    extends Handlers[Shape[A, E]](name, layer, shape) {

    private[this] val hIn         = new Handlers.InMain [A, E](this, shape.in0)()
    private[this] val hMaxDlyLen  = new Handlers.InIAux       (this, shape.in1)(math.max(0, _))
    private[this] val hDlyLen     = new Handlers.InIAux       (this, shape.in2)(math.max(0, _))
    private[this] val hOut        = new Handlers.OutMain[A, E](this, shape.out)
    private[this] var needsLen    = true
    private[this] var tailCleared = false
    private[this] var buf: Array[A] = _   // circular
    private[this] var maxDlyLen   = 0
    private[this] var dlyLen      = 0
    private[this] var bufLen      = 0
    private[this] var bufPosIn    = 0   // the base position before adding delay
    private[this] var bufPosOut   = 0
    private[this] var advance     = 0   // in-pointer ahead of out-pointer

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      checkInDone()
    }

    private def checkInDone(): Boolean = {
//      if (dlyLen < maxDlyLen) {
//        advance   = math.max(0, advance + dlyLen - maxDlyLen)
//        maxDlyLen = dlyLen
//      }
      val res = advance == 0 && hOut.flush()
      if (res) completeStage()
      res
    }

    protected def process(): Unit = {
      logStream(s"process() $this")

      if (needsLen) {
        if (!hMaxDlyLen.hasNext) return

        maxDlyLen = hMaxDlyLen.next()
        // because we always process in before out,
        // it is crucial that the buffer be _larger_ than the `maxDlyLen`
        bufLen    = ctrl.blockSize + maxDlyLen
        buf       = aTpe.newArray(bufLen)
        advance   = maxDlyLen
        bufPosIn  = maxDlyLen
        needsLen  = false
      }

      // always enter here -- `needsLen` must be `false` now
      while (true) {
        val remIn   = hIn.available
        val remOut  = math.min(hOut.available, hDlyLen.available)

        // never be ahead more than `bufLen` frames
        val numIn = math.min(remIn, bufLen - advance)
        if (numIn > 0) {
          val chunk = math.min(numIn, bufLen - bufPosIn)
          hIn.nextN(buf, bufPosIn, chunk)
          val chunk2 = numIn - chunk
          if (chunk2 > 0) {
            hIn.nextN(buf, 0, chunk2)
          }
          bufPosIn  = (bufPosIn + numIn) % bufLen
          advance  += numIn
        }

//        if (hIn.isDone && checkInDone()) return
        if (hIn.isDone && !tailCleared) {
          // clear dirty region
          val numClear = bufLen - advance
          if (numClear > 0) {
            val chunk = math.min(numClear, bufLen - bufPosIn)
            aTpe.clear(buf, bufPosIn, chunk)
            val chunk2 = numClear - chunk
            if (chunk2 > 0) {
              aTpe.clear(buf, 0, chunk2)
            }
          }
          advance    += numClear
          tailCleared = true
        }

        // N.B. `numOut` can be negative if `advance < maxDlyLen`
//        val maxOut = if (hIn.isDone) advance else advance - maxDlyLen
        val numOut = math.min(remOut, advance - maxDlyLen)
        if (numOut > 0) {
          if (hDlyLen.isConstant) { // more efficient
            dlyLen      = math.min(maxDlyLen, hDlyLen.next())
            val dlyPos  = (bufPosOut + maxDlyLen - dlyLen) % bufLen
            val chunk   = math.min(numOut, bufLen - dlyPos)
            hOut.nextN(buf, dlyPos, chunk)
            val chunk2  = numIn - chunk
            if (chunk2 > 0) {
              hOut.nextN(buf, 0, chunk2)
            }

          } else {
            var i = 0
            while (i < numOut) {
              dlyLen      = math.min(maxDlyLen, hDlyLen.next())
              val dlyPos  = (bufPosOut + maxDlyLen - dlyLen + i) % bufLen
              val v       = buf(dlyPos)
              hOut.next(v)
              i += 1
            }
          }

          bufPosOut = (bufPosOut + numOut) % bufLen
          advance  -= numOut
        }

        if (hIn.isDone && checkInDone()) return

        // N.B. `numOut` can be negative if `advance < maxDlyLen`
        if (numIn == 0 && numOut <= 0) return
      }
    }

    //    protected def processChunk(): Boolean = {
//      val len = math.min(inRemain, outRemain)
//      val res = len > 0
//      if (res) {
//        if (_init) {
//          maxLength = math.max(1, bufIn1.buf(0))
//          delayBuf  = new Array[Double](maxLength)
//          _init     = false
//        }
//        var inOffI  = inOff
//        var outOffI = outOff
//        val inArr   = bufIn0 .buf
//        val outArr  = bufOut0.buf
//        val dlyArr  = if (bufIn2 == null) null else bufIn2.buf
//        val dlyStop = if (bufIn2 == null) 0    else bufIn2.size
//        val dlyBuf  = delayBuf
//        var _delay  = delay
//        val _maxLen = maxLength
//        val _maxLen1= _maxLen - 1
//        var _bufWr  = bufPtr
//        var _bufRd  = (_bufWr - _delay + _maxLen) % _maxLen
//        var rem     = len
//        while (rem > 0) {
//          if (inOffI < dlyStop) {
//            _delay  = math.max(0, math.min(dlyArr(inOffI), _maxLen1))
//            _bufRd  = (_bufWr - _delay + _maxLen) % _maxLen
//          }
//          dlyBuf(_bufWr)  = inArr(inOffI)
//          outArr(outOffI) = dlyBuf(_bufRd)
//          _bufWr  += 1; if (_bufWr == _maxLen) _bufWr = 0
//          _bufRd  += 1; if (_bufRd == _maxLen) _bufRd = 0
//          inOffI  += 1
//          outOffI += 1
//          rem     -= 1
//        }
//        bufPtr      = _bufWr
//        delay       = _delay
//        inOff       = inOffI
//        outOff      = outOffI
//        inRemain   -= len
//        outRemain  -= len
//      }
//      res
//    }

//    protected def shouldComplete(): Boolean = inRemain == 0 && isClosed(in0) && !isAvailable(in0)
  }
}