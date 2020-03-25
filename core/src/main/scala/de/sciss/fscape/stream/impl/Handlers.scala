/*
 *  Handlers.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Inlet, Outlet, Shape}
import de.sciss.fscape.stream.{BufD, BufElem, BufI, Control, InD, InI, Layer, OutD, OutI, StreamType}
import de.sciss.fscape.{logStream => log}

/** In the mess of all the different implementation classes, this is a new
  * approach for collecting standard type of handlers which correctly handle the
  * issue of different polling frequencies and buffer sizes between inlets.
  * They are currently not optimised for array operations, but assume a simple
  * value by value polling.
  */
object Handlers {
  // ---- input ----

  private val idD: Double => Double = x => x
  private val idI: Int    => Int    = x => x
  private val idA: Any    => Any    = x => x

  final class InDMain(n: Handlers[_], inlet: InD)(cond: Double => Double = idD)
    extends AbstractInMain[Double, BufD](n, inlet)(cond) {

    private[this] val condId = cond eq idD

    override protected def condN(a: Array[Double], off: Int, len: Int): Unit =
      if (!condId) super.condN(a, off, len)
  }

  final class InIMain(n: Handlers[_], inlet: InI)(cond: Int => Int = idI)
    extends AbstractInMain[Int, BufI](n, inlet)(cond) {

    private[this] val condId = cond eq idI

    override protected def condN(a: Array[Int], off: Int, len: Int): Unit =
      if (!condId) super.condN(a, off, len)
  }

  final class InMain[A, E >: Null <: BufElem[A]](n: Handlers[_], inlet: Inlet[E])
                                                (cond: A => A = idA.asInstanceOf[A => A])
    extends AbstractInMain[A, E](n, inlet)(cond) {

    private[this] val condId = cond eq idA

    override protected def condN(a: Array[A], off: Int, len: Int): Unit =
      if (!condId) super.condN(a, off, len)
  }

  final class InDAux(n: Handlers[_], inlet: InD)(cond: Double => Double = idD)
    extends AbstractInAux[Double, BufD](n, inlet)(cond)

  final class InIAux(n: Handlers[_], inlet: InI)(cond: Int => Int = idI)
    extends AbstractInAux[Int, BufI](n, inlet)(cond)

  abstract class AbstractInAux[@specialized(Int, Long, Double) A, E >: Null <: BufElem[A]](n: Handlers[_],
                                                                                           inlet: Inlet[E])
                                                                                          (cond: A => A)
    extends InHandler {

    import n._

    override def toString: String = inlet.toString()

    private[this] var buf   : E       = _
    private[this] var off   : Int     = _
    private[this] var _value: A       = _
    private[this] var valid           = false
    private[this] var closedValid     = false
    private[this] var _hasNext        = false

    final def hasNext: Boolean = _hasNext

    final def peek: A = {
      require (_hasNext)

      // check that we are actually `@specialized:`
      // (new Exception).fillInStackTrace().printStackTrace()
      // it prints `	at de.sciss.fscape.stream.impl.Handlers$AbstractInAux$mcI$sp.peek$mcI$sp(Handlers.scala:65)`

      val _buf = buf
      if (buf != null) {
        _value = cond(_buf.buf(off))
      }
      _value
    }

    final def value: A = _value

    final def available: Int =
      if (_hasNext) {
        val _buf = buf
        if (_buf == null) Int.MaxValue else {
          _buf.size - off
        }
      } else 0

    final def next(): A = {
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

    final def onPush(): Unit = {
      val ok = buf == null
      log(s"$this onPush() - $ok")
      if (ok) {
        buf = grab(inlet)
        off = 0
        tryPull(inlet)
        signalNext()
      }
    }

    private def signalNext(): Unit = {
      assert (!_hasNext)
      _hasNext = true
      process() // ready
    }

    override def onUpstreamFinish(): Unit = {
      val ok = buf == null && !isAvailable(inlet)
      log(s"$this onUpstreamFinish() - $ok")
      if (ok) {
        if (valid) {
          closedValid = true
          signalNext()
        } else {
          completeStage()
        }
      }
    }

    final def free(): Unit =
      if (buf != null) {
        buf.release()
        buf = null
      }

    setHandler(inlet, this)
  }

  abstract class AbstractInMain[@specialized(Int, Long, Double) A, E >: Null <: BufElem[A]](n: Handlers[_],
                                                                                            inlet: Inlet[E])
                                                                                           (cond: A => A)
    extends InHandler {

    import n._

    protected def condN(a: Array[A], off: Int, len: Int): Unit = {
      var i     = off
      val stop  = off + len
      while (i < stop) {
        val v = cond(a(i))
        a(i) = v
        i += 1
      }
    }

    override def toString: String = inlet.toString()

    private[this] var buf   : E       = _
    private[this] var off   : Int     = _
    private[this] var _hasNext        = false
    private[this] var _isDone         = false

    final def hasNext : Boolean = _hasNext
    final def isDone  : Boolean = _isDone

    final def peek: A = {
      require (_hasNext)
      buf.buf(off)
    }

    final def available: Int =
      if (_hasNext) {
        buf.size - off
      } else 0

    final def next(): A = {
      require (_hasNext)
      val v = buf.buf(off)
      postNextN(1)
      v
    }

    private def bufExhausted(): Unit = {
      buf.release()
      if (isAvailable(inlet)) {
        doGrab()
      } else {
        buf = null
        _hasNext = false
        if (isClosed(inlet)) {
          _isDone = true
        }
      }
    }

    private def preNextN(len: Int): Unit = {
      require (_hasNext)
      val avail = buf.size - off
      require (len <= avail)
    }

    private def postNextN(len: Int): Unit = {
      off += len
      if (off == buf.size) {
        bufExhausted()
      }
    }

    final def nextN(a: Array[A], off: Int, len: Int): Unit = {
      preNextN  (len)
      System.arraycopy(buf.buf, this.off, a, off, len)
      postNextN (len)
    }

    final def skip(len: Int): Unit = {
      preNextN  (len)
      postNextN (len)
    }

    final def copy(to: AbstractOutMain[A, E], len: Int): Unit = {
      preNextN  (len)
      to.nextN(buf.buf, off, len)
      postNextN (len)
    }

    private def doGrab(): Unit = {
      val _buf = grab(inlet)
      buf = _buf
      off = 0
      condN(_buf.buf, 0, _buf.size)
      tryPull(inlet)
    }

    final def onPush(): Unit = {
      val ok = buf == null
      log(s"$this onPush() - $ok")
      if (ok) {
        doGrab()
        assert (!_hasNext)
        _hasNext = true
        process() // ready
      }
    }

    override def onUpstreamFinish(): Unit = {
      val ok = buf == null && !isAvailable(inlet)
      log(s"$this onUpstreamFinish() - $ok")
      if (ok) {
        _isDone = true
        onDone(inlet)
      }
    }

    final def free(): Unit =
      if (buf != null) {
        buf.release()
        buf = null
      }

    setHandler(inlet, this)
  }

  // ---- output ----

  final class OutDMain(n: Handlers[_], outlet: OutD)
    extends AbstractOutMain[Double, BufD](n, outlet) {

    import n._

    protected def mkBuf(): BufD = control.borrowBufD()
  }

  final class OutIMain(n: Handlers[_], outlet: OutI)
    extends AbstractOutMain[Int, BufI](n, outlet) {

    import n._

    protected def mkBuf(): BufI = control.borrowBufI()
  }

  final class OutMain[A, E >: Null <: BufElem[A]](n: Handlers[_], outlet: Outlet[E])(implicit tpe: StreamType[A, E])
    extends AbstractOutMain[A, E](n, outlet) {

    import n._

    protected def mkBuf(): E = tpe.allocBuf()
  }

  abstract class AbstractOutMain[@specialized(Int, Long, Double) A, E >: Null <: BufElem[A]](n: Handlers[_],
                                                                                             outlet: Outlet[E])
    extends OutHandler {

    protected def mkBuf(): E

    import n._

    override def toString: String = outlet.toString()

    private[this] var buf   : E       = _
    private[this] var off   : Int     = _
    private[this] var _hasNext        = true
    private[this] var _flush          = false
    private[this] var _isDone         = false

    final def hasNext : Boolean = _hasNext
    final def isDone  : Boolean = _isDone

    final def flush(): Boolean = {
      _flush    = true
      _hasNext  = false
      buf == null || {
        val now = isAvailable(outlet)
        if (now) {
          write()
          _isDone = true
        }
        now
      }
    }

    private def ensureBuf(): E = {
      var _buf = buf
      if (_buf == null) {
        _buf = mkBuf()
        buf = _buf
        off = 0
      }
      _buf
    }

    final def available: Int =
      if (_hasNext) {
        val _buf = ensureBuf()
        _buf.size - off
      } else 0

    private def postNextN(len: Int): Unit = {
      off += len
      if (off == buf.size) {
        if (isAvailable(outlet)) {
          write()
        } else {
          _hasNext = false
        }
      }
    }

    final def next(v: A): Unit = {
      require (_hasNext)
      val _buf = ensureBuf()
      _buf.buf(off) = v
      postNextN(1)
    }

    final def nextN(a: Array[A], off: Int, len: Int): Unit = {
      require (_hasNext)
      val _buf  = ensureBuf()
      val avail = _buf.size - this.off
      require (len <= avail)
      System.arraycopy(a, off, _buf.buf, this.off, len)
      postNextN(len)
    }

    final def onPull(): Unit = {
      val _buf  = buf
      val ok    = _buf != null && (off == _buf.size || _flush)
      log(s"$this onPull()")
      if (ok) {
        write()
        if (_flush) {
          _isDone   = true
          onDone(outlet)
        } else {
          _hasNext  = true
          process()
        }
      }
    }

    final override def onDownstreamFinish(cause: Throwable): Unit = {
      log(s"$this onDownstreamFinish()")
      _isDone   = true
      _hasNext  = false
      free()
      onDone(outlet)
      // super.onDownstreamFinish(cause)
    }

    private def write(): Unit = {
      if (off > 0) {
        buf.size = off
        push(outlet, buf)
      } else {
        buf.release()
      }
      buf = null
      // not necessary here: _off  = 0
    }

    final def free(): Unit =
      if (buf != null) {
        buf.release()
        buf = null
      }

    setHandler(outlet, this)
  }
}
abstract class Handlers[+S <: Shape](name: String, layer: Layer, shape: S)(implicit control: Control)
  extends NodeImpl[S](name, layer, shape) {

  protected def process(): Unit

  protected def onDone(inlet  : Inlet [_]): Unit
  protected def onDone(outlet : Outlet[_]): Unit = completeStage()
}