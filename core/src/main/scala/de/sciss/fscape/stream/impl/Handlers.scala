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

package de.sciss.fscape.stream
package impl

import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Inlet, Outlet, Shape}
import de.sciss.fscape.stream.impl.Handlers.Resource
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
  private val idL: Long   => Long   = x => x
//  private val idA: Any    => Any    = x => x

  def InIMain(n: Handlers[_], inlet: InI): InIMain = new InIMainImpl(n, inlet)
  def InLMain(n: Handlers[_], inlet: InL): InLMain = new InLMainImpl(n, inlet)
  def InDMain(n: Handlers[_], inlet: InD): InDMain = new InDMainImpl(n, inlet)

  def InMain[A, E <: BufElem[A]](n: Handlers[_], inlet: Inlet[E])
                                (implicit tpe: StreamType[A, E]): InMain[A, E] = {
    val res: InMain[_, _] = if (tpe.isDouble) {
      new InDMainImpl(n, inlet.asInstanceOf[InD])
    } else if (tpe.isInt) {
      new InIMainImpl(n, inlet.asInstanceOf[InI])
    } else if (tpe.isLong) {
      new InLMainImpl(n, inlet.asInstanceOf[InL])
    } else {
      new InAMainImpl[A, E](n, inlet)
    }
    res.asInstanceOf[InMain[A, E]]
  }

  def InIAux(n: Handlers[_], inlet: InI)(cond: Int    => Int    = idI): InIAux = new InIAuxImpl(n, inlet)(cond)
  def InLAux(n: Handlers[_], inlet: InL)(cond: Long   => Long   = idL): InLAux = new InLAuxImpl(n, inlet)(cond)
  def InDAux(n: Handlers[_], inlet: InD)(cond: Double => Double = idD): InDAux = new InDAuxImpl(n, inlet)(cond)

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

  sealed trait InOut extends Resource {
    /** Whether further input or output is available. */
    def hasNext: Boolean

    /** The number of further input or output frames available. */
    def available: Int
  }

  sealed trait Main[A, E <: BufElem[A]] extends InOut {
    def isDone  : Boolean

    /** Be sure you know what you are doing.
      * This should be called if efficient array
      * access is needed, only after making sure
      * that data is available. Valid array data
      * starts at `offset` for `available` number
      * of frames. To indicate that a number of
      * frames has been used, finally call `advance`.
      */
    def array: Array[A]

    /** Be sure you know what you are doing. */
    def offset: Int

    /** Be sure you know what you are doing. */
    def advance(len: Int): Unit
  }

  sealed trait In[A, E <: BufElem[A]] extends InOut with InHandler {
    def inlet: Inlet[E]

    /** Returns the next input value without advancing the
      * internal offset.
      */
    def peek: A

    /** Returns the next input value and advances the
      * internal offset. This may result in the old buffer
      * being freed and a new buffer being grabbed, so one
      * should always go back to check `hasNext` or `available`
      * in the same loop, until the input is exhausted.
      */
    def next(): A
  }

  sealed trait InAux[A, E <: BufElem[A]] extends In[A, E] {
    def value: A

    /** Returns `true` if the auxiliary input is completed (and closed)
      * and from now on the same constant value will be returned.
      * Similar to `isDone` on `Main` handlers, although an auxiliary
      * handler will never call back directly if the input is closed.
      */
    def isConstant: Boolean
  }

  /** Version specialized for `Int` */
  sealed trait InIAux extends InAux[Int, BufI] {
    final type E = BufI
    final type A = Int

    override def inlet: Inlet[E]

    override def peek  : A
    override def value : A
    override def next(): A

  }
  /** Version specialized for `Long` */
  sealed trait InLAux extends InAux[Long, BufL] {
    final type E = BufL
    final type A = Long

    override def inlet: Inlet[E]

    override def peek  : A
    override def value : A
    override def next(): A
  }
  /** Version specialized for `Double` */
  sealed trait InDAux extends InAux[Double, BufD] {
    final type E = BufD
    final type A = Double

    override def inlet: Inlet[E]

    override def peek  : A
    override def value : A
    override def next(): A
  }

  sealed trait InMain[A, E <: BufElem[A]] extends In[A, E] with Main[A, E] {
    /** Reads the next `len` frames into the given array. */
    def nextN(a: Array[A], off: Int, len: Int): Unit

    /** Skips over the next `len` frames without using them. */
    def skip(len: Int): Unit

    /** Directly copies `len` elements from this input to a given output. */
    def copyTo(to: OutMain[A, E], len: Int): Unit
  }

  /** Version specialized for `Int` */
  sealed trait InIMain extends InMain[Int, BufI] {
    final type E = BufI
    final type A = Int

    override def inlet: Inlet[E]

    override def peek  : A
    override def next(): A

    override def nextN(a: Array[A], off: Int, len: Int): Unit
  }
  /** Version specialized for `Long` */
  sealed trait InLMain extends InMain[Long, BufL] {
    final type E = BufL
    final type A = Long

    override def inlet: Inlet[E]

    override def peek  : A
    override def next(): A

    override def nextN(a: Array[A], off: Int, len: Int): Unit
  }
  /** Version specialized for `Double` */
  sealed trait InDMain extends InMain[Double, BufD] {
    final type E = BufD
    final type A = Double

    override def inlet: Inlet[E]

    override def peek  : A
    override def next(): A

    override def nextN(a: Array[A], off: Int, len: Int): Unit
  }
  
  // ---- output ----
  
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

  trait OutMain[A, E <: BufElem[A]] extends Main[A, E] with OutHandler {

    def outlet: Outlet[E]

    def flush(): Boolean

    def next(v: A): Unit

    def nextN(a: Array[A], off: Int, len: Int): Unit
  }

  /** Version specialized for `Int` */
  trait OutIMain extends OutMain[Int, BufI] {
    final type E = BufI
    final type A = Int

    override def outlet : Outlet[E]
    override def array  : Array[A]

    override def next(v: A): Unit

    override def nextN(a: Array[A], off: Int, len: Int): Unit
  }
  /** Version specialized for `Long` */
  sealed trait OutLMain extends OutMain[Long, BufL] {
    final type E = BufL
    final type A = Long

    override def outlet : Outlet[E]
    override def array  : Array[A]

    override def next(v: A): Unit

    override def nextN(a: Array[A], off: Int, len: Int): Unit
  }
  /** Version specialized for `Double` */
  sealed trait OutDMain extends OutMain[Double, BufD] {
    final type E = BufD
    final type A = Double

    override def outlet : Outlet[E]
    override def array  : Array[A]

    override def next(v: A): Unit

    override def nextN(a: Array[A], off: Int, len: Int): Unit
  }

  // ---- impl ----

  private abstract class InMainImpl[@specialized(Args) A, E <: BufElem[A]] private[Handlers] (n: Handlers[_],
                                                                           val inlet: Inlet[E])
    extends InMain[A, E] {

    import n._

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

    /** Be sure you know what you are doing */
    final def array: Array[A] = {
      require (_hasNext)
      buf.buf
    }

    /** Be sure you know what you are doing */
    final def offset: Int = off

    final def next(): A = {
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

    final def advance(len: Int): Unit = {
      off += len
      if (off == buf.size) {
        bufExhausted()
      }
    }

    final def nextN(a: Array[A], off: Int, len: Int): Unit = {
      preNextN(len)
      System.arraycopy(buf.buf, this.off, a, off, len)
      advance (len)
    }

    final def skip(len: Int): Unit = {
      preNextN(len)
      advance (len)
    }

    final def copyTo(to: OutMain[A, E], len: Int): Unit = {
      preNextN(len)
      to.nextN(buf.buf, off, len)
      advance (len)
    }

    private def doGrab(): Unit = {
      val _buf = grab(inlet)
      buf = _buf
      off = 0
      //      condN(_buf.buf, 0, _buf.size)
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

    override final def onUpstreamFinish(): Unit = {
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
        buf = null.asInstanceOf[E]
      }

    setHandler(inlet, this)
    addResource(this)
  }

  private final class InAMainImpl[A, E <: BufElem[A]](n: Handlers[_], inlet: Inlet[E])
    extends InMainImpl[A, E](n, inlet)

  private final class InIMainImpl(n: Handlers[_], inlet: InI)
    extends InMainImpl[Int, BufI](n, inlet) with InIMain

  private final class InLMainImpl(n: Handlers[_], inlet: InL)
    extends InMainImpl[Long, BufL](n, inlet) with InLMain

  private final class InDMainImpl(n: Handlers[_], inlet: InD)
    extends InMainImpl[Double, BufD](n, inlet) with InDMain
  
  private abstract class OutMainImpl[@specialized(Args) A, E <: BufElem[A]] private[Handlers] (n: Handlers[_],
                                                                            val outlet: Outlet[E])
                                                                           (implicit tpe: StreamType[A, E])
    extends OutMain[A, E] {

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