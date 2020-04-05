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
import de.sciss.fscape.stream.impl.Handlers.Resource
import de.sciss.fscape.stream.{BufD, BufElem, BufI, BufL, Control, InD, InI, InL, Layer, OutD, OutI, OutL, StreamType}
import de.sciss.fscape.{logStream => log}

import scala.Specializable.Args

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
  private val idL: Long   => Long   = x => x
//  private val idA: Any    => Any    = x => x

  type InDMain = InMain[Double, BufD]
  type InIMain = InMain[Int, BufI]

  def InIMain(n: Handlers[_], inlet: InI): InIMain = new InMain[Int   , BufI](n, inlet)
  def InDMain(n: Handlers[_], inlet: InD): InDMain = new InMain[Double, BufD](n, inlet)

  def InMain[A, E <: BufElem[A]](n: Handlers[_], inlet: Inlet[E])
                                (implicit tpe: StreamType[A, E]): InMain[A, E] = {
    val res: InMain[_, _] = if (tpe.isDouble) {
      new InMain[Double , BufD](n, inlet.asInstanceOf[InD])
    } else if (tpe.isInt) {
      new InMain[Int    , BufI](n, inlet.asInstanceOf[InI])
    } else if (tpe.isLong) {
      new InMain[Long   , BufL](n, inlet.asInstanceOf[InL])
    } else {
      new InMain[A, E](n, inlet)
    }
    res.asInstanceOf[InMain[A, E]]
  }

  def InDAux(n: Handlers[_], inlet: InD)(cond: Double => Double = idD): InDAux =
    new InDAuxImpl(n, inlet)(cond)

  def InIAux(n: Handlers[_], inlet: InI)(cond: Int => Int = idI): InIAux =
    new InIAuxImpl(n, inlet)(cond)

  def InLAux(n: Handlers[_], inlet: InL)(cond: Long => Long = idL): InLAux =
    new InLAuxImpl(n, inlet)(cond)

  def InAux[A, E <: BufElem[A]](n: Handlers[_], inlet: Inlet[E])(cond: A => A = identity[A] _)
                               (implicit tpe: StreamType[A, E]): InAux[A, E] = {
    val res: InAux[_, _] = if (tpe.isDouble) {
      new InDAuxImpl(n, inlet.asInstanceOf[InD])(cond.asInstanceOf[Double => Double ])
    } else if (tpe.isInt) {
      new InIAuxImpl(n, inlet.asInstanceOf[InI])(cond.asInstanceOf[Int    => Int    ])
    } else if (tpe.isLong) {
      new InLAuxImpl(n, inlet.asInstanceOf[InL])(cond.asInstanceOf[Long   => Long   ])
    } else {
      new InAAuxImpl[A, E](n, inlet)(cond)
    }
    res.asInstanceOf[InAux[A, E]]
  }

  trait Resource {
    def free(): Unit
  }

  trait InAux[A, E <: BufElem[A]] extends InHandler with Resource {
    def inlet: Inlet[E]

    def hasNext: Boolean

    def peek  : A
    def value : A
    def next(): A

    def isConstant: Boolean

    def available: Int
  }

  trait InIAux extends InAux[Int    , BufI]
  trait InLAux extends InAux[Long   , BufL]
  trait InDAux extends InAux[Double , BufD]

  final class InMain[/*@specialized(Args)*/ A, E <: BufElem[A]] private[Handlers] (n: Handlers[_],
                                                                               val inlet: Inlet[E])
    extends InHandler with Resource {

    import n._

    override def toString: String = inlet.toString()

    private[this] var buf   : E       = _
    private[this] var off   : Int     = _
    private[this] var _hasNext        = false
    private[this] var _isDone         = false

    def hasNext : Boolean = _hasNext
    def isDone  : Boolean = _isDone

    def peek: A = {
      require (_hasNext)
      buf.buf(off)
    }

    def available: Int =
      if (_hasNext) {
        buf.size - off
      } else 0

    /** Be sure you know what you are doing */
    def array: Array[A] = {
      require (_hasNext)
      buf.buf
    }

    /** Be sure you know what you are doing */
    def offset: Int = off

    def next(): A = {
      require (_hasNext)
      val v = buf.buf(off)
      advance(1)
      v
    }

    private def bufExhausted(): Unit = {
      buf.release()
      if (isAvailable(inlet)) {
        doGrab()
      } else {
        buf = null.asInstanceOf[E]
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

    def advance(len: Int): Unit = {
      off += len
      if (off == buf.size) {
        bufExhausted()
      }
    }

    def nextN(a: Array[A], off: Int, len: Int): Unit = {
      preNextN(len)
      System.arraycopy(buf.buf, this.off, a, off, len)
      advance (len)
    }

    def skip(len: Int): Unit = {
      preNextN  (len)
      advance (len)
    }

    def copyTo(to: OutMain[A, E], len: Int): Unit = {
      preNextN  (len)
      to.nextN(buf.buf, off, len)
      advance (len)
    }

//    final def mapTo[B, F <: BufElem[B]](to: AbstractOutMain[B, F], len: Int)(f: A => B): Unit = {
//      preNextN  (len)
//      to.mapNextN(buf.buf, off, len)
//      postNextN (len)
//    }

    private def doGrab(): Unit = {
      val _buf = grab(inlet)
      buf = _buf
      off = 0
//      condN(_buf.buf, 0, _buf.size)
      tryPull(inlet)
    }

    def onPush(): Unit = {
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

    def free(): Unit =
      if (buf != null) {
        buf.release()
        buf = null.asInstanceOf[E]
      }

    setHandler(inlet, this)
    addResource(this)
  }

  // ---- output ----

  trait OutIMain extends OutMain[Int   , BufI]
  trait OutLMain extends OutMain[Long  , BufL]
  trait OutDMain extends OutMain[Double, BufD]

  def OutIMain(n: Handlers[_], outlet: OutI): OutIMain = new OutIMainImpl(n, outlet)
  def OutLMain(n: Handlers[_], outlet: OutL): OutLMain = new OutLMainImpl(n, outlet)
  def OutDMain(n: Handlers[_], outlet: OutD): OutDMain = new OutDMainImpl(n, outlet)

  def OutMain[A, E <: BufElem[A]](n: Handlers[_], outlet: Outlet[E])
                                 (implicit tpe: StreamType[A, E]): OutMain[A, E] = {
    val res: OutMain[_, _] = if (tpe.isDouble) {
      new OutDMainImpl(n, outlet.asInstanceOf[OutD])
    } else if (tpe.isInt) {
      new OutIMainImpl(n, outlet.asInstanceOf[OutI])
    } else if (tpe.isLong) {
      new OutLMainImpl(n, outlet.asInstanceOf[OutL])
    } else {
      new OutAMainImpl[A, E](n, outlet)
    }
    res.asInstanceOf[OutMain[A, E]]
  }

  trait OutMain[A, E <: BufElem[A]]
    extends OutHandler with Resource {

    def outlet: Outlet[E]

    def hasNext : Boolean
    def isDone  : Boolean

    def flush(): Boolean

    def array: Array[A]

    def offset: Int

    def available: Int

    def advance(len: Int): Unit

    def next(v: A): Unit

    def nextN(a: Array[A], off: Int, len: Int): Unit
  }

  // ---- impl ----

  private abstract class OutMainImpl[A, E <: BufElem[A]] private[Handlers] (n: Handlers[_],
                                                                            val outlet: Outlet[E])
                                                                           (implicit tpe: StreamType[A, E])
    extends OutMain[A, E] with OutHandler with Resource {

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

    final def array: Array[A] = {
      require (_hasNext)
      ensureBuf().buf
    }

    final def offset: Int = off

    private def ensureBuf(): E = {
      var _buf = buf
      if (_buf == null) {
        _buf = tpe.allocBuf()
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

    final def advance(len: Int): Unit = {
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
      advance(1)
    }

    final def nextN(a: Array[A], off: Int, len: Int): Unit = {
      require (_hasNext)
      val _buf  = ensureBuf()
      val avail = _buf.size - this.off
      require (len <= avail)
      System.arraycopy(a, off, _buf.buf, this.off, len)
      advance(len)
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

    override def onDownstreamFinish(cause: Throwable): Unit = {
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
      buf = null.asInstanceOf[E]
      // not necessary here: _off  = 0
    }

    def free(): Unit =
      if (buf != null) {
        buf.release()
        buf = null.asInstanceOf[E]
      }

    setHandler(outlet, this)
    addResource(this)
  }

  private final class OutAMainImpl[A, E <: BufElem[A]](n: Handlers[_], outlet: Outlet[E])
                                                      (implicit tpe: StreamType[A, E])
    extends OutMainImpl[A, E](n, outlet)

  private final class OutIMainImpl(n: Handlers[_], outlet: OutI)
    extends OutMainImpl[Int, BufI](n, outlet) with OutIMain

  private final class OutLMainImpl(n: Handlers[_], outlet: OutL)
    extends OutMainImpl[Long, BufL](n, outlet) with OutLMain

  private final class OutDMainImpl(n: Handlers[_], outlet: OutD)
    extends OutMainImpl[Double, BufD](n, outlet) with OutDMain

  private abstract class InAuxImpl[@specialized(Args) A, E <: BufElem[A]](n: Handlers[_], final val inlet: Inlet[E])
                                                                         (cond: A => A)
    extends InAux[A, E] {

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

    /** Returns `true` if the inlet is closed and a valid value had been obtained
      * which will thus be the "constant" value returned henceforth.
      */
    def isConstant: Boolean = closedValid

    final def available: Int =
      if (_hasNext) {
        val _buf = buf
        if (_buf == null) Int.MaxValue else {
          _buf.size - off
        }
      } else 0

    def next(): A = {
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
            buf = null.asInstanceOf[E]
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

    override final def onUpstreamFinish(): Unit = {
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
        buf = null.asInstanceOf[E]
      }

    setHandler(inlet, this)
    addResource(this)
  }

  private final class InAAuxImpl[A, E <: BufElem[A]](n: Handlers[_], inlet: Inlet[E])
                                                    (cond: A => A)
    extends InAuxImpl[A, E](n, inlet)(cond)

  private final class InIAuxImpl(n: Handlers[_], inlet: InI)(cond: Int => Int)
    extends InAuxImpl[Int, BufI](n, inlet)(cond) with InIAux

  private final class InLAuxImpl(n: Handlers[_], inlet: InL)(cond: Long => Long)
    extends InAuxImpl[Long, BufL](n, inlet)(cond) with InLAux

  private final class InDAuxImpl(n: Handlers[_], inlet: InD)(cond: Double => Double)
    extends InAuxImpl[Double, BufD](n, inlet)(cond) with InDAux
}
abstract class Handlers[+S <: Shape](name: String, layer: Layer, shape: S)(implicit control: Control)
  extends NodeImpl[S](name, layer, shape) {

  private[this] final var resources: List[Resource] = Nil

  protected final def addResource(r: Resource): Unit = resources ::= r

  override protected def stopped(): Unit = {
    super.stopped()
    resources.reverseIterator.foreach(_.free())
    resources = Nil
  }

  protected def process(): Unit

  protected def onDone(inlet  : Inlet [_]): Unit
  protected def onDone(outlet : Outlet[_]): Unit = completeStage()
}