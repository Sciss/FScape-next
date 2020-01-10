/*
 *  Histogram.scala
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

package de.sciss.fscape.stream

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape6}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec

object Histogram {
  def apply(in: OutD, bins: OutI, lo: OutD, hi: OutD, mode: OutI, reset: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(bins  , stage.in1)
    b.connect(lo    , stage.in2)
    b.connect(hi    , stage.in3)
    b.connect(mode  , stage.in4)
    b.connect(reset , stage.in5)
    stage.out
  }

  private final val name = "Histogram"

  private type Shape = FanInShape6[BufD, BufI, BufD, BufD, BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.bins"  ),
      in2 = InD (s"$name.lo"    ),
      in3 = InD (s"$name.hi"    ),
      in4 = InI (s"$name.mode"  ),
      in5 = InI (s"$name.reset" ),
      out = OutI(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit control: Control)
    extends NodeImpl(name, layer, shape) {

    private[this] var histogram: Array[Int] = _
    private[this] var init    = true

    private[this] val hIn     = new InDMainHandler(shape.in0)(identity)
    private[this] val hBins   = new InIAuxHandler(shape.in1)(math.max(1, _))
    private[this] val hLo     = new InDAuxHandler(shape.in2)(identity)
    private[this] val hHi     = new InDAuxHandler(shape.in3)(identity)
    private[this] val hMode   = new InIAuxHandler(shape.in4)(_.clip(0, 1))
    private[this] val hReset  = new InIAuxHandler(shape.in5)(identity)
    private[this] val hOut    = new OutIMainHandler(shape.out)

    override protected def stopped(): Unit = {
      hIn   .free()
      hBins .free()
      hLo   .free()
      hHi   .free()
      hMode .free()
      hReset.free()
      hOut  .free()
      histogram = null
    }

    private final class OutIMainHandler(outlet: OutI)(implicit control: Control)
      extends OutHandler {

      private[this] var buf   : BufI    = _
      private[this] var off   : Int     = _
      private[this] var _hasNext        = true
      private[this] var _flush          = false
      private[this] var _isDone         = false

      def hasNext : Boolean = _hasNext
      def isDone  : Boolean = _isDone

      def flush(): Boolean = {
        _flush    = true
        _hasNext  = false
        val now = isAvailable(outlet)
        if (now) {
          push(outlet, buf)
          buf   = null
        }
        now
      }

      def next(v: Int): Unit = {
        require (_hasNext)
        var _buf = buf
        var _off = off
        if (_buf == null) {
          _buf  = control.borrowBufI()
          buf   = buf
          _off  = 0
        }
        _buf.buf(_off) = v
        _off += 1
        if (_off == _buf.size) {
          if (isAvailable(outlet)) {
            push(outlet, _buf)
            buf   = null
            // not necessary here: _off  = 0
          } else {
            _hasNext = false
          }
        }
        off = _off
      }

      def onPull(): Unit = {
        val _buf = buf
        if (_buf != null && (off == _buf.size || _flush)) {
          push(outlet, _buf)
          buf = null
          if (_flush) {
            _isDone   = true
          } else {
            _hasNext  = true
          }
          process()
        }
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        _isDone = true
        process()
        // super.onDownstreamFinish(cause)
      }

      def free(): Unit =
        if (buf != null) {
          buf.release()
          buf = null
        }

      setHandler(outlet, this)
    }

    private final class InDMainHandler(inlet: InD)(cond: Double => Double) // (ready: => Unit)
      extends InHandler {

      private[this] var buf   : BufD    = _
      private[this] var off   : Int     = _
      private[this] var _hasNext        = false
      private[this] var _isDone         = false

      def hasNext : Boolean = _hasNext
      def isDone  : Boolean = _isDone

      def peek: Double = {
        require (_hasNext)
        val _buf = buf
        cond(_buf.buf(off))
      }

      def next(): Double = {
        require (_hasNext)
        val _buf = buf
        var _off = off
        val v = cond(_buf.buf(_off))
        _off += 1
        if (_off == _buf.size) {
          _buf.release()
          if (isAvailable(inlet)) {
            buf = grab(inlet)
            off = 0
            tryPull(inlet)
          } else {
            buf = null
            _hasNext = false
            if (isClosed(inlet)) {
              _isDone = true
            }
          }

        } else {
          off = _off
        }
        v
      }

      def onPush(): Unit = if (buf == null) {
        buf = grab(inlet)
        off = 0
        tryPull(inlet)
        signalNext()
      }

      private def signalNext(): Unit = {
        assert (!_hasNext)
        _hasNext = true
        process() // ready
      }

      override def onUpstreamFinish(): Unit = {
        if (buf == null && !isAvailable(inlet)) {
          _isDone = true
          process()
        }
      }

      def free(): Unit =
        if (buf != null) {
          buf.release()
          buf = null
        }

      setHandler(inlet, this)
    }

    private final class InDAuxHandler(inlet: InD)(cond: Double => Double) // (ready: => Unit)
      extends InHandler {

      private[this] var buf   : BufD    = _
      private[this] var off   : Int     = _
      private[this] var _value: Double  = _
      private[this] var valid           = false
      private[this] var closedValid     = false
      private[this] var _hasNext        = false

      def hasNext: Boolean = _hasNext

      def peek: Double = {
        require (_hasNext)
        val _buf = buf
        if (buf != null) {
          _value = cond(_buf.buf(off))
        }
        _value
      }

      def value: Double = _value

      def next(): Double = {
        require (_hasNext)
        val _buf = buf
        if (buf != null) {
          var _off = off
          _value = cond(_buf.buf(_off))
          if (!valid) valid = true
          _off += 1
          if (_off == _buf.size) {
            _buf.release()
            if (isAvailable(inlet)) {
              buf = grab(inlet)
              off = 0
              tryPull(inlet)
            } else {
              buf = null
              if (isClosed(inlet)) {
                closedValid = true
              } else {
                _hasNext = false
              }
            }

          } else {
            off = _off
          }
        }
        _value
      }

      def onPush(): Unit = if (buf == null) {
        buf = grab(inlet)
        off = 0
        tryPull(inlet)
        signalNext()
      }

      private def signalNext(): Unit = {
        assert (!_hasNext)
        _hasNext = true
        process() // ready
      }

      override def onUpstreamFinish(): Unit = {
        if (buf == null && !isAvailable(inlet)) {
          if (valid) {
            closedValid = true
            signalNext()
          } else {
            completeStage()
          }
        }
      }

      def free(): Unit =
        if (buf != null) {
          buf.release()
          buf = null
        }

      setHandler(inlet, this)
    }

    private final class InIAuxHandler(inlet: InI)(cond: Int => Int)
      extends InHandler {

      private[this] var buf   : BufI    = _
      private[this] var off   : Int     = _
      private[this] var _value: Int     = _
      private[this] var valid           = false
      private[this] var closedValid     = false
      private[this] var _hasNext        = false

      def hasNext: Boolean = _hasNext

      def peek: Int = {
        require (_hasNext)
        val _buf = buf
        if (buf != null) {
          _value = cond(_buf.buf(off))
        }
        _value
      }

      def value: Int = _value

      def next(): Int = {
        require (_hasNext)
        val _buf = buf
        if (buf != null) {
          var _off = off
          _value = cond(_buf.buf(_off))
          if (!valid) valid = true
          _off += 1
          if (_off == _buf.size) {
            _buf.release()
            if (isAvailable(inlet)) {
              buf = grab(inlet)
              off = 0
              tryPull(inlet)
            } else {
              buf = null
              if (isClosed(inlet)) {
                closedValid = true
              } else {
                _hasNext = false
              }
            }

          } else {
            off = _off
          }
        }
        _value
      }

      def onPush(): Unit = if (buf == null) {
        buf = grab(inlet)
        off = 0
        tryPull(inlet)
        signalNext()
      }

      private def signalNext(): Unit = {
        assert (!_hasNext)
        _hasNext = true
        process() // ready
      }

      override def onUpstreamFinish(): Unit = {
        if (buf == null && !isAvailable(inlet)) {
          if (valid) {
            closedValid = true
            signalNext()
          } else {
            completeStage()
          }
        }
      }

      def free(): Unit =
        if (buf != null) {
          buf.release()
          buf = null
        }

      setHandler(inlet, this)
    }

    private[this] var histogramOff  = 0
    private[this] var stage         = 0 // 0 -- read, 1 -- write

    @tailrec
    private def process(): Unit = {
      if (hOut.isDone) {
        completeStage()
        return
      }

      if (stage == 0) { // read
        while (stage == 0) {
          if (!(hIn.hasNext && hOut.hasNext && hReset.hasNext && hLo.hasNext && hHi.hasNext)) return

          val r = hReset.peek > 0
          if (r || init) {
            if (!(hBins.hasNext && hMode.hasNext)) return
            val bins = hBins.next()
            if (histogram == null || histogram.length != bins) {
              histogram = new Array(bins)
            } else {
              Util.clear(histogram, 0, bins)
            }
            hMode.next()
            if (init) init = false
          }
          hReset.next() // commit

          val lo    = hLo.next()
          val hi    = hHi.next()
          val bins  = histogram.length
          val binsM = bins - 1
          val in    = hIn.next()
          val inC   = in.clip(lo, hi)
          val bin   = math.min(binsM, inC.linLin(lo, hi, 0, bins).toInt)
          histogram(bin) += 1

          if (hMode.value == 1 || hIn.isDone) {
            stage = 1
          }
        }

      } else {  // write
        while (stage == 1) {
          if (!hOut.hasNext) return

          var _off  = histogramOff
          val bins  = histogram.length
          hOut.next(histogram(_off))
          _off += 1
          if (_off == bins) {
            stage = 0
            if (hIn.isDone) {
              if (hOut.flush()) {
                completeStage()
                return
              }
            }
          } else {
            histogramOff = _off
          }
        }
      }

      process()
    }
  }
}