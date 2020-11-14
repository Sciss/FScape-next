/*
 *  SlidingPlatform.scala
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

import java.io.RandomAccessFile
import java.nio.channels.FileChannel

import de.sciss.file.File
import de.sciss.fscape.stream.Sliding.{Logic, Shp, Window, WindowD, WindowI, WindowL}

trait SlidingPlatform {
  private final class DisposeFile(f: File, raf: RandomAccessFile) extends (() => Unit) {
    def apply(): Unit = {
      raf.close()
      f.delete()
      ()
    }
  }

  protected final class LogicD(shape: Shp[BufD], layer: Layer)(implicit ctrl: Control)
    extends Logic[Double, BufD](shape, layer) with LogicPlatform[Double, BufD] {

    type A = Double

    protected def mkMemWindow(sz: Int): Window[A] = new WindowD(new Array(sz))

    def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A] = {
      val fch   = raf.getChannel
      val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, sz * 8)
      val db    = bb.asDoubleBuffer()
      new WindowD(db, sz, new DisposeFile(f, raf))
    }
  }

  protected final class LogicI(shape: Shp[BufI], layer: Layer)(implicit ctrl: Control)
    extends Logic[Int, BufI](shape, layer) with LogicPlatform[Int, BufI] {

    type A = Int

    protected def mkMemWindow(sz: Int): Window[A] = new WindowI(new Array(sz))

    def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A] = {
      val fch   = raf.getChannel
      val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, sz * 4)
      val db    = bb.asIntBuffer()
      new WindowI(db, sz, new DisposeFile(f, raf))
    }
  }

  protected final class LogicL(shape: Shp[BufL], layer: Layer)(implicit ctrl: Control)
    extends Logic[Long, BufL](shape, layer) with LogicPlatform[Long, BufL] {

    type A = Long

    protected def mkMemWindow(sz: Int): Window[A] = new WindowL(new Array(sz))

    def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A] = {
      val fch   = raf.getChannel
      val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, sz * 8)
      val db    = bb.asLongBuffer()
      new WindowL(db, sz, new DisposeFile(f, raf))
    }
  }

  protected trait LogicPlatform[A, E <: BufElem[A]] {

    protected def mkMemWindow(sz: Int): Window[A]

    protected def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A]

    protected def mkWindow(sz: Int)(implicit ctrl: Control): Window[A] =
      if (sz <= ctrl.nodeBufferSize) {
        mkMemWindow(sz)
      } else {
        val f     = ctrl.createTempFile()
        val raf   = new RandomAccessFile(f, "rw")
        mkDiskWindow(sz, f, raf)
      }
  }
}
