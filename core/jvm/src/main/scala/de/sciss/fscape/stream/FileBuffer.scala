/*
 *  FileBuffer.scala
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

import java.io.RandomAccessFile
import java.nio.{Buffer, ByteBuffer, DoubleBuffer, IntBuffer, LongBuffer}

import de.sciss.file.File

import scala.math.min

object FileBuffer {
  /** Uses the `Control` to allocate a new temporary file, which is deleted upon disposal. */
  def apply[A, E <: BufElem[A]]()(implicit ctrl: Control, tpe: StreamType[A, E]): FileBuffer[A] = {
    val f = ctrl.createTempFile()
    apply[A, E](f, deleteOnDispose = true)
  }

  def double()(implicit ctrl: Control): FileBufferD = {
    val f = ctrl.createTempFile()
    new ImplD(f, deleteOnDispose = true)
  }

  def int()(implicit ctrl: Control): FileBufferI = {
    val f = ctrl.createTempFile()
    new ImplI(f, deleteOnDispose = true)
  }

  def long()(implicit ctrl: Control): FileBufferL = {
    val f = ctrl.createTempFile()
    new ImplL(f, deleteOnDispose = true)
  }

  def apply[A, E <: BufElem[A]](file: File, deleteOnDispose: Boolean)
                               (implicit tpe: StreamType[A, E]): FileBuffer[A] = {
    val res: FileBuffer[_] = if (tpe.isDouble) {
      new ImplD(file, deleteOnDispose = deleteOnDispose)
    } else if (tpe.isInt) {
      new ImplI(file, deleteOnDispose = deleteOnDispose)
    } else {
      assert (tpe.isLong)
      new ImplL(file, deleteOnDispose = deleteOnDispose)
    }
    res.asInstanceOf[FileBuffer[A]]
  }

  private abstract class Base[A](val file: File, deleteOnDispose: Boolean) extends FileBuffer[A] {
    override def toString = s"FileBuffer($file)"

    private[this] val raf = new RandomAccessFile(file, "rw")
    private[this] val ch  = raf.getChannel
    private[this] var bb  = null: ByteBuffer

    protected def dataShift: Int

    final def position        : Long        = ch.position() >> dataShift
    final def position_=(value: Long): Unit = {
      ch.position(value << dataShift)
      ()
    }

    final def dispose(): Unit = {
      ch.close()
      if (deleteOnDispose) {
        file.delete()
        ()
      }
    }

    protected def newBuffer(bb: ByteBuffer): Unit

    final protected def ensureBuf(len: Long): Unit = {
      val lim   = math.min(len, 8192).toInt
      val limB  = lim << dataShift
      if (bb == null || bb.capacity() < limB) {
        bb = ByteBuffer.allocate(limB)
        newBuffer(bb) // db = bb.asDoubleBuffer()
      }
    }

    protected def clearAndPut(buf: Array[A], off: Int, len: Int): Unit

    def write(buf: Array[A], off: Int, len: Int): Unit = {
      ensureBuf(len)
      var off0 = off
      var len0 = len
      while (len0 > 0) {
        val chunk = min(8192, len0)
        clearAndPut(buf, off0, chunk)
//        db.clear()
//        db.put(buf, off0, chunk)
        (bb: Buffer).rewind().limit(chunk << dataShift)
        ch.write(bb)
        len0 -= chunk
        off0 += chunk
      }
    }

    final def numFrames: Long = ch.size() >> dataShift

    final def rewind(): Unit = position = 0L

    final protected def postWriteValue(len: Long): Unit = {
      var len0 = len
      while (len0 > 0) {
        val chunk = min(8192, len0).toInt
        (bb: Buffer).rewind().limit(chunk << dataShift)
        ch.write(bb)
        len0 -= chunk
      }
    }

    protected def clearAndGet(buf: Array[A], off: Int, len: Int): Unit

    final def read(buf: Array[A], off: Int, len: Int): Unit = {
      ensureBuf(len)
      var off0 = off
      var len0 = len
      while (len0 > 0) {
        val chunk = math.min(8192, len0)
        (bb: Buffer).rewind().limit(chunk << dataShift)
        ch.read(bb)
        clearAndGet(buf, off0, chunk)
//        db.clear()
//        db.get(buf, off0, chunk)
        len0 -= chunk
        off0 += chunk
      }
    }
  }

  private final class ImplD(file: File, deleteOnDispose: Boolean)
    extends Base[Double](file, deleteOnDispose = deleteOnDispose) with FileBufferD {

    final val dataShift = 3

    private[this] var tb  = null: DoubleBuffer

    protected def newBuffer(bb: ByteBuffer): Unit =
      tb = bb.asDoubleBuffer()

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

    def writeValue(value: A, len: Long): Unit = {
      ensureBuf(len)
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

  private final class ImplI(file: File, deleteOnDispose: Boolean)
    extends Base[Int](file, deleteOnDispose = deleteOnDispose) with FileBufferI {

    final val dataShift = 2

    private[this] var tb  = null: IntBuffer

    protected def newBuffer(bb: ByteBuffer): Unit =
      tb = bb.asIntBuffer()

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

    def writeValue(value: A, len: Long): Unit = {
      ensureBuf(len)
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

  private final class ImplL(file: File, deleteOnDispose: Boolean)
    extends Base[Long](file, deleteOnDispose = deleteOnDispose) with FileBufferL {

    final val dataShift = 3

    private[this] var tb  = null: LongBuffer

    protected def newBuffer(bb: ByteBuffer): Unit =
      tb = bb.asLongBuffer()

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

    def writeValue(value: A, len: Long): Unit = {
      ensureBuf(len)
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
  * As opposed to `AudioFile`, it supports 64-bit resolution and does
  * not perform any internal caching.
  */
trait FileBuffer[A] {
  def file: File

  var position: Long
  def numFrames: Long

  /** Same as `position = 0L`. */
  def rewind(): Unit

  def dispose(): Unit

  def read (buf: Array[A], off: Int, len: Int): Unit
  def write(buf: Array[A], off: Int, len: Int): Unit

  def writeValue(value: A, len: Long): Unit
}

trait FileBufferD extends FileBuffer[Double] {
  type A = Double

  override def read (buf: Array[A], off: Int, len: Int): Unit
  override def write(buf: Array[A], off: Int, len: Int): Unit

  override def writeValue(value: A, len: Long): Unit
}

trait FileBufferI extends FileBuffer[Int] {
  type A = Int

  override def read (buf: Array[A], off: Int, len: Int): Unit
  override def write(buf: Array[A], off: Int, len: Int): Unit

  override def writeValue(value: A, len: Long): Unit
}

trait FileBufferL extends FileBuffer[Long] {
  type A = Long

  override def read (buf: Array[A], off: Int, len: Int): Unit
  override def write(buf: Array[A], off: Int, len: Int): Unit

  override def writeValue(value: A, len: Long): Unit
}