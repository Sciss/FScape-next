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

      A     B     C     D

      |--size--|

      where the first and third peak are spaced further than `size`.
      We cannot 'drop' A until we know that B will be surpassed by C.
      But then the situation repeats - we cannot 'drop' B until we
      know whether C will be surpassed by D?

      The suggested output here is:
      - A is encountered and stored
      - B is encountered and stored
      - C is encountered -- A is accepted and B dropped
      - D is encountered
      - the input ends; D is accepted and C is dropped

      (2)
                             |
                       |     |
                 |     |     |
      |          |     |     |

      A          B     C     D

      |--size--|

      - A is encountered and stored
      - no more peaks are seen for `size` frames; A is accepted
      - B is encountered and stored
      - C is encountered and stored
      - D is encountered -- B is accepted and C is dropped
      - the input ends; D is accepted

      (3)
                  |
                  |     |
      |           |     |
      |     |     |     |

      A     B     C     D

      |--size--|

      - A is encountered and stored
      - B is encountered, but smaller than A, B is dropped
      - no more peaks are seen for `size` frames after A's position; A is accepted
      - C is encountered and stored
      - D is encountered, but smaller than A, B is dropped
      - the input ends; C is accepted

      Algorithm
      =========

      - there are two slots for past maxima; there can be zero, one, or two elements stored at a time
      - for non-zero slots, we set the maximum frame advance so that the last element just drops out of the window
      - if frame advance is reached without encountering any more maxima, the last element is accepted,
        and the slots are cleared
      - if another maxima is found exceeding the last element, we decide:
        - if only one slot was used, we store the new maximum to the second slot
        - if both slots were used, we accept the first element, we drop the second element,
          and we store the new maximum (as the only element) in the slots.
      - when input end is reached, we accept the last slot element if any

      XXX TODO:
      Flaw: We should accept more than two slots; indeed size/2 slots would be the maximum number of slots?

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