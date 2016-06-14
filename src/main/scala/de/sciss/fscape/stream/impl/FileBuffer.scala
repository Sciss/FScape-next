/*
 *  FileBuffer.scala
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
package impl

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

    // also clears `db` and limits `bb`
    private def ensureBuf(len: Int): Unit = {
      if (db == null || db.capacity() < len) {
        bb = ByteBuffer.allocate(len << 3)
        db = bb.asDoubleBuffer()
      }
      db.clear()
      bb.rewind().limit(len << 3)
    }

    def write(buf: Array[Double], off: Int, len: Int): Unit = {
      ensureBuf(len)
      db.put(buf, off, len)
      ch.write(bb)
    }

    def read(buf: Array[Double], off: Int, len: Int): Unit = {
      ensureBuf(len)
      ch.read(bb)
      db.get(buf, off, len)
    }
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

  def dispose(): Unit

  def read (buf: Array[Double], off: Int, len: Int): Unit
  def write(buf: Array[Double], off: Int, len: Int): Unit
}