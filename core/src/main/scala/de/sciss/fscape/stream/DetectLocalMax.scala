/*
 *  DetectLocalMax.scala
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
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec

object DetectLocalMax {
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): OutI = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "DetectLocalMax"

  private type Shp[E] = FanInShape2[E, BufI, BufI]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape2(
      in0 = Inlet[E](s"$name.in"  ),
      in1 = InI       (s"$name.gate"),
      out = OutI      (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic[A, E](shape, layer)
  }

  /*
      Need to be able to handle the following situations:

      (1)
                        |
                  |     |
            |     |     |
      |     |     |     |

      A     B     C     D       --> B, D

      |--size--|

      where the first and third peak are spaced further than `size`.
      Should we keep A until we know that B will be surpassed by C?
      But then the situation repeats - we cannot 'drop' B until we
      know whether C will be surpassed by D?

      It seems there are only two good strategies:
      - we build a global memory and then descend from the
        highest known peak to its left and right. This would
        be essentially the priority queue, but unbounded and
        with a spatial post-processing stage.
      - we proceed strictly sequentially. once we detected
        a local maximum, the frame count begins, and at the
        end of that window, simply the highest detected maximum
        would be emitted. Advantage: much simpler, no
        random access needed. Disadvantage: we could lose the
        highest maxima if we are unlucky.

      We'll go for the sequential strategy, and could still
      implement the optimal one as `DetectGlobalMax`.

      The suggested output then here is:
      - A is encountered and stored; frame count begins
      - B is encountered and replaces A
      - frame count exceeds window size;
        B is emitted and frame count reset to B's position
      - C is encountered in "dead zone" and skipped
      - D is encountered; frame count begins
      - the input ends; D is emitted

      (2)
                             |
                       |     |
                 |     |     |
      |          |     |     |

      A          B     C     D       --> A, C  (sub-optimal compared to global search yielding A, B, D)

      |--size--|

      - A is encountered and stored
      - no more peaks are seen for `size` frames; A is accepted
      - B is encountered and stored; frame count begins
      - C is encountered and replaces B
      - D is in "dead" zone and skipped
      - frame count exceeds window size; C is emitted
      - the input ends

      (3)
                  |
                  |     |
      |           |     |
      |     |     |     |

      A     B     C     D       --> A, C

      |--size--|

      - A is encountered and stored
      - B is encountered, but smaller and skipped
      - no more peaks are seen for `size` frames after A's position; A is accepted
      - C is encountered and stored
      - D is encountered, but smaller than A, B is dropped
      - the input ends; C is accepted

      Algorithm
      =========

      - state is 'empty'
      - loop:
        - if state is 'blocked', skip until stop frame has been reached. if stream terminates, terminate.
          otherwise, set state to 'empty', then loop.
        - if state is 'empty', scan input for next local maximum. if stream terminates, terminate.
          if max is found, store it, set state to 'found', and set stop frame to found frame plus window size,
          then loop.
        - if state is 'found', scan input for next local maximum greater than stored max,
          but no further than stop frame. if stream terminates, emit stored max, then terminate.
          if a new larger max is found, replace stored max, then loop. if no larger max is found,
          emit stored max, set state to 'blocked', set stop frame to found frame plus window size, then loop.

   */

  private final class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                               (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    private[this] val hIn     = Handlers.InMain[A, E] (this, shape.in0)
    private[this] val hSize   = Handlers.InIAux       (this, shape.in1)(math.max(1, _)) // XXX TODO -- allow zero?
    private[this] val hOut    = Handlers.OutIMain     (this, shape.out)

    private[this] var size          = 0

    private[this] var framesRead    = 0L
    private[this] var framesWritten = 0L
    private[this] var state         = 0     // 0 - empty, 1 - found, 2 - blocked
    private[this] var foundValue: A = _
    private[this] var foundFrame    = 0L
    private[this] var stopFrame     = 0L
    private[this] var writtenTrig   = 0L
    private[this] var x0: A         = _
    private[this] var s0: Int       = 0     // slope signum (<0, == 0, >0)

    private[this] var readMode      = true
    private[this] var _complete     = false

    import tpe.ordering

    private def shouldComplete(): Boolean = _complete && framesWritten == framesRead

    protected def onDone(inlet: Inlet[_]): Unit =
      process()

    @tailrec
    protected def process(): Unit = {
      val active = if (readMode) processRead() else processWrite()
      if (shouldComplete()) {
        if (hOut.flush()) completeStage()
      } else if (active) process()
    }

//    protected def processChunk(): Boolean =
//      if (readMode) processRead() else processWrite()

    private def processRead(): Boolean = {
      var inRemain = math.min(hIn.available, hSize.available)
      if (inRemain == 0 /*&& !_complete*/) {
        val terminate = hIn.isDone // isClosed(shape.in0) && !isAvailable(shape.in0)
        if (terminate) {
          if (state != 1) {
            foundFrame = framesRead // place a virtual found frame at the end
          }
          state       = 2 // stay in this mode (we will never switch back to read-mode)
//          state       = if (state == 1) 2 else 0
          debug(s"up-stream terminated. state -> $state")
          readMode    = false
          _complete   = true
        }
        terminate

      } else {
        var stateChanged = false
        // if state is 'empty', scan input for next local maximum. if stream terminates, terminate.
        // if max is found, store it, set state to 'found', and set stop frame to found frame plus window size

        // if state is 'blocked', skip until stop frame has been reached.
        // if stream terminates, terminate. otherwise, set state to 'empty'

        // if state is 'found', scan input for next local maximum greater than stored max,
        // but no further than stop frame. if stream terminates, emit stored max, then terminate.
        // if a new larger max is found, replace stored max, then loop. if no larger max is found,
        // emit stored max, set state to 'blocked', set stop frame to found frame plus window size

        def calcSkip(): Int = if (state == 0) inRemain else math.min(inRemain, stopFrame - framesRead).toInt

        var skip      = calcSkip()

        debug(s"read $framesRead - state $state skip $skip stop-frame $stopFrame")

        if (skip > 0) stateChanged = true
        // val _sizeStop = if (bufIn1 == null) 0 else bufIn1.size
//        val _in       = hIn.array // bufIn0.buf
//        val inOff     = hIn.offset
        var _x0       = x0
        var _s0       = s0
        while (skip > 0) {
          val x1      = hIn.next() // _in(inOff)
          val s1      = ordering.compare(x1, _x0)
//          if (inOff < _sizeStop) size = math.max(1, bufIn1.buf(inOff))
          size = hSize.next()

          if (state != 2) {
            val isMax = _s0 > 0 && s1 < 0
            if (isMax) {
              if (state == 0) {
                foundFrame  = framesRead - 1

                // the following two alternative definitions yield
                // very different outcomes. The first `foundFrame + size`
                // tends to produce "more sparse" results, but is better
                // at catching the highest peaks. The second
                // `max(writtenTrig + size, foundFrame)` is more dense,
                // but also performs worse in getting the highest peaks.

                stopFrame   = foundFrame + size + 1
//                stopFrame   = math.max(writtenTrig + size, framesRead) + 1
                foundValue  = _x0
                state       = 1
                debug(s"local max $foundFrame; stop-frame $stopFrame; state -> $state")
              } else {  // state == 1
                debug(s"local max $framesRead...")
                if (ordering.gt(_x0, foundValue)) {
                  foundFrame  = framesRead - 1
                  foundValue  = _x0
                  debug("...is larger")
                }
              }
            }
          }

          inRemain   -= 1
//          inOff      += 1
          framesRead += 1
          _x0         = x1
          _s0         = s1
          skip        = calcSkip()
        }
        x0 = _x0
        s0 = _s0

        if (state != 0) {
          val reachedStopFrame  = framesRead == stopFrame
          val terminate         = inRemain == 0 && isClosed(shape.in0) && !isAvailable(shape.in0)
          debug(s"reachedStopFrame? $reachedStopFrame; terminate? $terminate")
          if (reachedStopFrame || terminate) {
            // go from 'found' to 'blocked', from 'blocked' to 'empty'
            if (terminate) {
//              foundFrame  = framesRead // place a virtual found frame at the end
//              state       = 2
              _complete   = true
//            } else {
//              state = if (state == 1) 2 else 0
            }
            state = if (state == 1) 2 else 0
            debug(s"... state -> $state")
            if (state == 2) readMode = false

            stateChanged = true
          }
        }

        stateChanged
      }
    }

    private[this] val DEBUG = false

    private def debug(what: => String): Unit = if (DEBUG) println(what)

    private def processWrite(): Boolean = {
      var stateChanged = false

      var outRemain = hOut.available
      val chunk0    = if (state == 0) outRemain else math.min(outRemain, foundFrame - framesWritten).toInt
      val chunk     = if (_complete) math.min(chunk0, framesRead - framesWritten).toInt else chunk0

      debug(s"write $framesWritten - state $state chunk $chunk")

      if (chunk > 0) {
        val out     = hOut.array
        val outOff  = hOut.offset
        Util.clear(out, outOff, chunk)
        hOut.advance(chunk)
        outRemain     -= chunk
        framesWritten += chunk
        stateChanged   = true
      }

      if (state != 0) {
        val reachedFoundFrame = framesWritten == foundFrame
        if (reachedFoundFrame && outRemain > 0) {
          debug(s"... and consume trigger ------------------- $foundFrame")
//          println(s" T: $foundValue")
          if (!_complete || framesWritten < framesRead) {
            hOut.next(1)
//            bufOut0.buf(outOff) = 1
//            outOff             += 1
            outRemain          -= 1
            framesWritten      += 1
            stopFrame           = foundFrame + size + 1
            //          stopFrame = math.min(stopFrame, framesRead)
            writtenTrig         = foundFrame
            readMode            = true  // continue 'blocked' read
            stateChanged        = true
          }
        }
      }

      stateChanged
    }
  }
}