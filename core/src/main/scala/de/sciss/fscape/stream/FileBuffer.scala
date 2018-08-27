/*
 *  FileBuffer.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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
import java.nio.{ByteBuffer, DoubleBuffer}

import de.sciss.file.File

object FileBuffer {
  /** Uses the `Control` to allocate a new temporary file. */
  def apply()(implicit ctrl: Control): FileBuffer = new Impl(ctrl.createTempFile(), deleteOnDispose = true)

  def apply(file: File, deleteOnDispose: Boolean): FileBuffer = new Impl(file, deleteOnDispose = deleteOnDispose)

  private final class Impl(val file: File, deleteOnDispose: Boolean) extends FileBuffer {
    override def toString = s"FileBuffer($file)"

    private[this] val raf = new RandomAccessFile(file, "rw")
    private[this] val ch  = raf.getChannel
    private[this] var bb  = null: ByteBuffer
    private[this] var db  = null: DoubleBuffer

    def position        : Long        = ch.position() / 8
    def position_=(value: Long): Unit = ch.position(value * 8)

    def dispose(): Unit = {
      ch.close()
      if (deleteOnDispose) file.delete()
    }

    def numFrames: Long = ch.size() / 8 // raf.length() / 8

    private def ensureBuf(len: Long): Unit = {
      val lim = math.min(len, 8192).toInt
      if (db == null || db.capacity() < lim) {
        bb = ByteBuffer.allocate(lim << 3)
        db = bb.asDoubleBuffer()
      }
    }

    def write(buf: Array[Double], off: Int, len: Int): Unit = {
      ensureBuf(len)
      var off0 = off
      var len0 = len
      while (len0 > 0) {
        val chunk = math.min(8192, len0)
        db.clear()
        db.put(buf, off0, chunk)
        bb.rewind().limit(chunk << 3)
        ch.write(bb)
        len0 -= chunk
        off0 += chunk
      }
    }

    def writeValue(value: Double, len: Long): Unit = {
      ensureBuf(len)
      val sz = math.min(len, db.capacity()).toInt
      db.clear()
      var i = 0
      while (i < sz) {
        db.put(value)
        i += 1
      }
      var len0 = len
      while (len0 > 0) {
        val chunk = math.min(8192, len0).toInt
        bb.rewind().limit(chunk << 3)
        ch.write(bb)
        len0 -= chunk
      }
    }

    def read(buf: Array[Double], off: Int, len: Int): Unit = {
      ensureBuf(len)
      var off0 = off
      var len0 = len
      while (len0 > 0) {
        val chunk = math.min(8192, len0)
        bb.rewind().limit(chunk << 3)
        ch.read(bb)
        db.clear()
        db.get(buf, off0, chunk)
        len0 -= chunk
        off0 += chunk
      }
    }

    def rewind(): Unit = position = 0L
  }
}
/** A file buffer is similar to a monophonic 64-bit float sound file.
  * As opposed to `AudioFile`, it supports 64-bit resolution and does
  * not perform any internal caching.
  */
trait FileBuffer {
  def file: File

  var position: Long
  def numFrames: Long

  /** Same as `position = 0L`. */
  def rewind(): Unit

  def dispose(): Unit

  def read (buf: Array[Double], off: Int, len: Int): Unit
  def write(buf: Array[Double], off: Int, len: Int): Unit

  def writeValue(value: Double, len: Long): Unit
}