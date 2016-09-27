/*
 *  DetectLocalMax.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{ChunkImpl, FilterIn2Impl, NodeImpl, StageImpl}

object DetectLocalMax {
  def apply[A, Buf >: Null <: BufElem[A]](in: Outlet[Buf], size: OutI)
                                         (implicit b: Builder, tpe: StreamType[A, Buf]): OutI = {
    val stage0  = new Stage[A, Buf]
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    stage.out
  }

  private final val name = "DetectLocalMax"

  private type Shape[A, Buf >: Null <: BufElem[A]] = FanInShape2[Buf, BufI, BufI]

  private final class Stage[A, Buf >: Null <: BufElem[A]](implicit ctrl: Control, tpe: StreamType[A, Buf])
    extends StageImpl[Shape[A, Buf]](name) {

    val shape = new FanInShape2(
      in0 = Inlet[Buf](s"$name.in"  ),
      in1 = InI       (s"$name.gate"),
      out = OutI      (s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic[A, Buf](shape)
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

  private final class Logic[A, Buf >: Null <: BufElem[A]](shape: Shape[A, Buf])
                                                         (implicit ctrl: Control, tpe: StreamType[A, Buf])
    extends NodeImpl(name, shape)
      with FilterIn2Impl[Buf, BufI, BufI]
      with ChunkImpl[Shape[A, Buf]] {

    private[this] var high  = false
    private[this] var held  = 0.0

    protected def allocOutBuf0(): BufI = ctrl.borrowBufI()

    protected def shouldComplete(): Boolean = ???

    protected def processChunk(): Boolean = {
      val chunk = math.min(inRemain, outRemain)
      val res   = chunk > 0
      if (res) {
        processChunk(inOff = inOff, outOff = outOff, len = chunk)
        inOff       += chunk
        inRemain    -= chunk
        outOff      += chunk
        outRemain   -= chunk
      }
      res

      ???
    }

    protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit = {
      val b0      = bufIn0.buf
      val b1      = if (bufIn1 == null) null else bufIn1.buf
      val stop1   = if (b1     == null) 0    else bufIn1.size
      val out     = bufOut0.buf
      var h0      = high
      var v0      = held
      var inOffI  = inOff
      var outOffI = outOff
      val stop0   = inOff + len
      while (inOffI < stop0) {
        if (inOffI < stop1) h0 = b1(inOffI) > 0
        if (h0) {
          ??? // v0 = b0(inOffI)
        }
        ??? // out(outOffI) = v0
        inOffI  += 1
        outOffI += 1
      }
      high = h0
      held = v0
    }
  }
}