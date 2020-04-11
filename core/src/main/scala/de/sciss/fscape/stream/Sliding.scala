/*
 *  Sliding.scala
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
import java.nio.channels.FileChannel
import java.nio.{DoubleBuffer, IntBuffer, LongBuffer}

import akka.stream.{Attributes, FanInShape3, Inlet, Outlet}
import de.sciss.file.File
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.min

/** Sliding overlapping window. */
object Sliding {
  /**
    * @param in     the signal to window
    * @param size   the window size. this is clipped to be `&lt;= 1`
    * @param step   the step size. this is clipped to be `&lt;= 1`.
    *               If step size is larger than window size, frames in
    *               the input are skipped.
    */
  def apply[A, E <: BufElem[A]](in: Outlet[E], size: OutI, step: OutI)
                               (implicit b: Builder, tpe: StreamType[A, E]): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer)
    val stage   = b.add(stage0)
    b.connect(in  , stage.in0)
    b.connect(size, stage.in1)
    b.connect(step, stage.in2)
    stage.out
  }

  private final val name = "Sliding"

  private type Shp[E] = FanInShape3[E, BufI, BufI, E]

  private final class Stage[A, E <: BufElem[A]](layer: Layer)(implicit ctrl: Control, tpe: StreamType[A, E])
    extends StageImpl[Shp[E]](name) {

    val shape: Shape = new FanInShape3(
      in0 = Inlet [E] (s"$name.in"  ),
      in1 = InI       (s"$name.size"),
      in2 = InI       (s"$name.step"),
      out = Outlet[E] (s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = {
      val res: Logic[_, _] = if (tpe.isDouble) {
        new LogicD(shape.asInstanceOf[Shp[BufD]], layer)
      } else if (tpe.isInt) {
        new LogicI(shape.asInstanceOf[Shp[BufI]], layer)
      } else {
        require (tpe.isLong)
        new LogicL(shape.asInstanceOf[Shp[BufL]], layer)
      }
      res.asInstanceOf[Logic[A, E]]
    }
  }

  private abstract class Window[A](final val size0: Int, f: File, raf: RandomAccessFile) {

    def writeFrom (arr: Array[A], off: Int, num: Int): Unit
    def readTo    (arr: Array[A], off: Int, num: Int): Unit

    final protected var offIn : Int  = 0
    final protected var offOut: Int  = 0
    final protected var size  : Int  = size0

    final def zero(): Unit = offIn  = size
    final def trim(): Unit = size   = offIn

    final def inRemain    : Int = size  - offIn
    final def availableOut: Int = offIn - offOut
    final def outRemain   : Int = size  - offOut

    final def dispose(): Unit = if (raf != null) {
      raf.close()
      f.delete()
    }

    override def toString = s"Window(offIn = $offIn, offOut = $offOut, size = $size)"
  }

  private final class WindowD(buf: DoubleBuffer, size0: Int, f: File, raf: RandomAccessFile)
    extends Window[Double](size0, f, raf) {

    type A = Double

    def this(arr: Array[Double]) = this(DoubleBuffer.wrap(arr), arr.length, null, null)

    def writeFrom(arr: Array[A], off: Int, num: Int): Unit = {
      buf.position(offIn)
      buf.put(arr, off, num)
      offIn += num
    }

    def readTo(arr: Array[A], off: Int, num: Int): Unit = {
      buf.position(offOut)
      buf.get(arr, off, num)
      offOut += num
    }
  }

  private final class WindowI(buf: IntBuffer, size0: Int, f: File, raf: RandomAccessFile)
    extends Window[Int](size0, f, raf) {

    type A = Int

    def this(arr: Array[Int]) = this(IntBuffer.wrap(arr), arr.length, null, null)

    def writeFrom(arr: Array[A], off: Int, num: Int): Unit = {
      buf.position(offIn)
      buf.put(arr, off, num)
      offIn += num
    }

    def readTo(arr: Array[A], off: Int, num: Int): Unit = {
      buf.position(offOut)
      buf.get(arr, off, num)
      offOut += num
    }
  }

  private final class WindowL(buf: LongBuffer, size0: Int, f: File, raf: RandomAccessFile)
    extends Window[Long](size0, f, raf) {

    type A = Long

    def this(arr: Array[Long]) = this(LongBuffer.wrap(arr), arr.length, null, null)

    def writeFrom(arr: Array[A], off: Int, num: Int): Unit = {
      buf.position(offIn)
      buf.put(arr, off, num)
      offIn += num
    }

    def readTo(arr: Array[A], off: Int, num: Int): Unit = {
      buf.position(offOut)
      buf.get(arr, off, num)
      offOut += num
    }
  }

  private final class LogicD(shape: Shp[BufD], layer: Layer)(implicit ctrl: Control)
    extends Logic[Double, BufD](shape, layer) {

    type A = Double

    protected def mkMemWindow(sz: Int): Window[A] = new WindowD(new Array(sz))

    def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A] = {
      val fch   = raf.getChannel
      val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, sz * 8)
      val db    = bb.asDoubleBuffer()
      new WindowD(db, sz, f, raf)
    }
  }

  private final class LogicI(shape: Shp[BufI], layer: Layer)(implicit ctrl: Control)
    extends Logic[Int, BufI](shape, layer) {

    type A = Int

    protected def mkMemWindow(sz: Int): Window[A] = new WindowI(new Array(sz))

    def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A] = {
      val fch   = raf.getChannel
      val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, sz * 4)
      val db    = bb.asIntBuffer()
      new WindowI(db, sz, f, raf)
    }
  }

  private final class LogicL(shape: Shp[BufL], layer: Layer)(implicit ctrl: Control)
    extends Logic[Long, BufL](shape, layer) {

    type A = Long

    protected def mkMemWindow(sz: Int): Window[A] = new WindowL(new Array(sz))

    def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A] = {
      val fch   = raf.getChannel
      val bb    = fch.map(FileChannel.MapMode.READ_WRITE, 0L, sz * 8)
      val db    = bb.asLongBuffer()
      new WindowL(db, sz, f, raf)
    }
  }

  private abstract class Logic[A, E <: BufElem[A]](shape: Shp[E], layer: Layer)
                                                  (implicit ctrl: Control, tpe: StreamType[A, E])
    extends Handlers(name, layer, shape) {

    protected def mkMemWindow (sz: Int): Window[A]

    protected def mkDiskWindow(sz: Int, f: File, raf: RandomAccessFile): Window[A]

    private[this] val hIn     = Handlers.InMain [A, E](this, shape.in0)
    private[this] val hSize   = Handlers.InIAux       (this, shape.in1)(math.max(1, _)) // XXX TODO -- allow zero?
    private[this] val hStep   = Handlers.InIAux       (this, shape.in2)(math.max(1, _)) // XXX TODO -- allow zero?
    private[this] val hOut    = Handlers.OutMain[A, E](this, shape.out)

    private[this] var size  : Int  = _
    private[this] var step  : Int  = _

    private[this] val windows       = mutable.Buffer.empty[Window[A]]
    private[this] var windowFrames  = 0

    private[this] var isNextWindow  = true
    private[this] var stepRemain    = 0

    /*
      back-pressure algorithm:
      - never begin a step if windows.head is full
      - for example with a constant step size of 1/4 window size,
        this means we halt processing input after window size
        input frames (i.e. with four windows in memory).
     */
    @inline
    private def canPrepareStep = stepRemain == 0 && (windows.isEmpty || windows.head.inRemain > 0)

    private def shouldComplete(): Boolean = hIn.isDone && windows.isEmpty

    override protected def stopped(): Unit = {
      super.stopped()
      windows.foreach(_.dispose())
      windows.clear()
    }

    final protected def onDone(inlet: Inlet[_]): Unit =
      process()

    @tailrec
    final protected def process(): Unit = {
      val ok = processChunk()
      if (shouldComplete()) {
        if (hOut.flush()) completeStage()

      } else if (ok) process()
    }

    private def processChunk(): Boolean = {
      if (canPrepareStep && isNextWindow) {
        if (!tryObtainWinParams()) return false
        stepRemain    = step
        isNextWindow  = false
      }

      var stateChange = false

      val chunkIn = min(stepRemain, hIn.available)
      if (chunkIn > 0) {
        copyInputToWindows(chunkIn)
        stepRemain  -= chunkIn
        stateChange  = true

        if (stepRemain == 0) {
          isNextWindow = true
          stateChange  = true
        }

      } else if (hIn.isDone) { // flush
        var i = 0
        while (i < windows.length - 1) {
          val win = windows(i)
          if (win.inRemain > 0) {
            win.zero()  // 'zeroed'
            stateChange = true
          }
          i += 1
        }
        if (windows.nonEmpty) {
          val win = windows.last
          if (win.inRemain > 0) {
            win.trim()   // 'trimmed'
            stateChange = true
          }
        }
      }

      val chunkOut = hOut.available
      if (chunkOut > 0) {
        val chunk1 = copyWindowsToOutput(chunkOut)
        if (chunk1 > 0) {
          stateChange  = true
        }
      }

      stateChange
    }

    private def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext && hStep.hasNext
      if (ok) {
        size = hSize.next()
        step = hStep.next()

        val sz  = size
        val win: Window[A] = if (sz <= ctrl.nodeBufferSize) {
          mkMemWindow(sz)
        } else {
          val f     = ctrl.createTempFile()
          val raf   = new RandomAccessFile(f, "rw")
          mkDiskWindow(sz, f, raf)
        }
        windows += win
        windowFrames += sz
      }
      ok
    }

    private def copyInputToWindows(chunk: Int): Unit = {
      val in    = hIn.array
      val inOff = hIn.offset
      var i = 0
      while (i < windows.length) {
        val win = windows(i)
        val chunk1 = min(win.inRemain, chunk)
        if (chunk1 > 0) {
          win.writeFrom(in, inOff, chunk1)
        }
        i += 1
      }
      hIn.advance(chunk)
    }

    private def copyWindowsToOutput(chunk: Int): Int = {
      var chunk0  = chunk
      val out     = hOut.array
      var outOff0 = hOut.offset
      var i = 0
      while (chunk0 > 0 && i < windows.length) {  // take care of index as we drop windows on the way
        val win     = windows(i)
        val chunk1  = min(chunk0, win.availableOut)
        if (chunk1 > 0) {
          win.readTo(out, outOff0, chunk1)
          chunk0     -= chunk1
          outOff0    += chunk1
        }
        if (win.outRemain == 0) {
          windows.remove(i)
          windowFrames -= win.size0
          win.dispose()
        } else {
          i = windows.length
        }
      }
      val res = chunk - chunk0
      hOut.advance(res)
      res
    }
  }
}