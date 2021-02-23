/*
 *  AsyncFileBuffer.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import de.sciss.asyncfile.{AsyncFile, AsyncWritableByteChannel}

import java.net.URI
import java.nio.{Buffer, ByteBuffer}
import scala.concurrent.{ExecutionContext, Future}
import scala.math.min

object AsyncFileBuffer {
  /** Uses the `Control` to allocate a new temporary file, which is deleted upon disposal. */
  def apply[A, E <: BufElem[A]]()(implicit ctrl: Control, tpe: StreamType[A, E]): Future[AsyncFileBuffer[A]] = {
    val f = ctrl.createTempURI()
    apply[A, E](f, deleteOnDispose = true)
  }

  private def withFile[A](fun: (URI, AsyncWritableByteChannel) => A)(implicit ctrl: Control): Future[A] = {
    val f = ctrl.createTempURI()
    import ctrl.config.executionContext
    val futCh = AsyncFile.openWrite(f)
    futCh.map { ch =>
      fun(f, ch)
    }
  }

  def double()(implicit ctrl: Control): Future[AsyncFileBufferD] =
    withFile { (f, ch) =>
      import ctrl.config.executionContext
      new ImplD(f, ch, deleteOnDispose = true)
    }

  def int()(implicit ctrl: Control): Future[AsyncFileBufferI] =
    withFile { (f, ch) =>
      import ctrl.config.executionContext
      new ImplI(f, ch, deleteOnDispose = true)
    }

  def long()(implicit ctrl: Control): Future[AsyncFileBufferL] =
    withFile { (f, ch) =>
      import ctrl.config.executionContext
      new ImplL(f, ch, deleteOnDispose = true)
    }

  def apply[A, E <: BufElem[A]](file: URI, deleteOnDispose: Boolean)
                               (implicit ctrl: Control, tpe: StreamType[A, E]): Future[AsyncFileBuffer[A]] = {
    import ctrl.config.executionContext
    val futCh = AsyncFile.openWrite(file)
    futCh.map { ch =>
      val res: AsyncFileBuffer[_] = if (tpe.isDouble) {
        new ImplD(file, ch, deleteOnDispose = deleteOnDispose)
      } else if (tpe.isInt) {
        new ImplI(file, ch, deleteOnDispose = deleteOnDispose)
      } else {
        assert (tpe.isLong)
        new ImplL(file, ch, deleteOnDispose = deleteOnDispose)
      }
      res.asInstanceOf[AsyncFileBuffer[A]]
    }
  }

  private abstract class Base[A](val file: URI, ch: AsyncWritableByteChannel, deleteOnDispose: Boolean,
                                 dataShift: Int)
                                (implicit exec: ExecutionContext)
    extends AsyncFileBuffer[A] {

    override def toString = s"AsyncFileBuffer($file)"

    private[this] val sync = new AnyRef

    private[this] final val bufFrames = 4096

    protected final val bb: ByteBuffer = ByteBuffer.allocate(bufFrames << dataShift)

    final def position        : Long        = ch.position >> dataShift
    final def position_=(value: Long): Unit = {
      ch.position_=(value << dataShift)
      ()
    }

    final def dispose(): Future[Unit] = {
      val fs        = ch.fileSystem
      val futClose  = ch.close()
      if (!deleteOnDispose) futClose else futClose.map { _ =>
        fs.delete(file)
      }
    }

    protected def clearAndPut(buf: Array[A], off: Int, len: Int): Unit

    def write(buf: Array[A], off: Int, len: Int): Future[Unit] =
      if (len <= 0) Future.unit else {
        val chunk = min(bufFrames, len)
        // N.B.: "Buffers are not safe for use by multiple concurrent threads.
        // If a buffer is to be used by more than one thread then access to the buffer
        // should be controlled by appropriate synchronization."
        val fut = sync.synchronized {
          clearAndPut(buf, off, chunk)
          (bb: Buffer).rewind().limit(chunk << dataShift)
          ch.write(bb)
        }
        fut.flatMap { _ =>
          write(buf, off = off + chunk, len = len - chunk)
        }
      }

    final def numFrames: Long = ch.size >> dataShift

    final def rewind(): Unit = position = 0L

    final protected def postWriteValue(len: Long): Future[Unit] =
      if (len <= 0) Future.unit else {
        val chunk = min(bufFrames, len).toInt
        val fut = sync.synchronized {
          (bb: Buffer).rewind().limit(chunk << dataShift)
          ch.write(bb)
        }
        fut.flatMap { _ =>
          postWriteValue(len = len - chunk)
        }
      }

    protected def clearAndGet(buf: Array[A], off: Int, len: Int): Unit

    final def read(buf: Array[A], off: Int, len: Int): Future[Unit] =
      if (len <= 0) Future.unit else {
        val chunk = min(bufFrames, len)
        val fut = sync.synchronized {
          (bb: Buffer).rewind().limit(chunk << dataShift)
          ch.read(bb)
        }
        fut.flatMap { _ =>
          // N.B.: "Buffers are not safe for use by multiple concurrent threads.
          // If a buffer is to be used by more than one thread then access to the buffer
          // should be controlled by appropriate synchronization."
          sync.synchronized {
            clearAndGet(buf, off, chunk)
          }
          read(buf, off = off + chunk, len = len - chunk)
        }
      }
  }

  private final class ImplD(file: URI, ch: AsyncWritableByteChannel, deleteOnDispose: Boolean)
                           (implicit exec: ExecutionContext)
    extends Base[Double](file, ch, deleteOnDispose = deleteOnDispose, dataShift = 3)
      with AsyncFileBufferD {

    private[this] val tb = bb.asDoubleBuffer()

    protected def clearAndPut(buf: Array[A], off: Int, len: Int): Unit = {
      (tb: Buffer).clear()
      tb.put(buf, off, len)
      ()
    }

    protected def clearAndGet(buf: Array[A], off: Int, len: Int): Unit = {
      (tb: Buffer).clear()
      tb.get(buf, off, len)
      ()
    }

    override def writeValue(value: A, len: Long): Future[Unit] = {
      val sz = min(len, tb.capacity()).toInt
      (tb: Buffer).clear()
      var i = 0
      while (i < sz) {
        tb.put(value)
        i += 1
      }
      postWriteValue(len)
    }
  }

  private final class ImplI(file: URI, ch: AsyncWritableByteChannel, deleteOnDispose: Boolean)
                           (implicit exec: ExecutionContext)
    extends Base[Int](file, ch, deleteOnDispose = deleteOnDispose, dataShift = 2)
      with AsyncFileBufferI {

    private[this] val tb = bb.asIntBuffer()

    protected def clearAndPut(buf: Array[A], off: Int, len: Int): Unit = {
      (tb: Buffer).clear()
      tb.put(buf, off, len)
      ()
    }

    protected def clearAndGet(buf: Array[A], off: Int, len: Int): Unit = {
      (tb: Buffer).clear()
      tb.get(buf, off, len)
      ()
    }

    override def writeValue(value: A, len: Long): Future[Unit] = {
      val sz = min(len, tb.capacity()).toInt
      (tb: Buffer).clear()
      var i = 0
      while (i < sz) {
        tb.put(value)
        i += 1
      }
      postWriteValue(len)
    }
  }

  private final class ImplL(file: URI, ch: AsyncWritableByteChannel, deleteOnDispose: Boolean)
                           (implicit exec: ExecutionContext)
    extends Base[Long](file, ch, deleteOnDispose = deleteOnDispose, dataShift = 3)
      with AsyncFileBufferL {

    private[this] val tb = bb.asLongBuffer()

    protected def clearAndPut(buf: Array[A], off: Int, len: Int): Unit = {
      (tb: Buffer).clear()
      tb.put(buf, off, len)
      ()
    }

    protected def clearAndGet(buf: Array[A], off: Int, len: Int): Unit = {
      (tb: Buffer).clear()
      tb.get(buf, off, len)
      ()
    }

    override def writeValue(value: A, len: Long): Future[Unit] = {
      val sz = min(len, tb.capacity()).toInt
      (tb: Buffer).clear()
      var i = 0
      while (i < sz) {
        tb.put(value)
        i += 1
      }
      postWriteValue(len)
    }
  }
}
/** A file buffer is similar to a monophonic 64-bit float sound file.
  * As opposed to `AsyncAudioFile`, it exposes other array types to the use site.
  */
trait AsyncFileBuffer[A] {
  def file: URI

  var position: Long
  def numFrames: Long

  /** Same as `position = 0L`. */
  def rewind(): Unit

  def dispose(): Future[Unit]

  def read (buf: Array[A], off: Int, len: Int): Future[Unit]
  def write(buf: Array[A], off: Int, len: Int): Future[Unit]

  /** Repeatedly writes the same value, `len` times. */
  def writeValue(value: A, len: Long): Future[Unit]
}

trait AsyncFileBufferD extends AsyncFileBuffer[Double] {
  type A = Double

  override def read (buf: Array[A], off: Int, len: Int): Future[Unit]
  override def write(buf: Array[A], off: Int, len: Int): Future[Unit]

  override def writeValue(value: A, len: Long): Future[Unit]
}

trait AsyncFileBufferI extends AsyncFileBuffer[Int] {
  type A = Int

  override def read (buf: Array[A], off: Int, len: Int): Future[Unit]
  override def write(buf: Array[A], off: Int, len: Int): Future[Unit]

  override def writeValue(value: A, len: Long): Future[Unit]
}

trait AsyncFileBufferL extends AsyncFileBuffer[Long] {
  type A = Long

  override def read (buf: Array[A], off: Int, len: Int): Future[Unit]
  override def write(buf: Array[A], off: Int, len: Int): Future[Unit]

  override def writeValue(value: A, len: Long): Future[Unit]
}