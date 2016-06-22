/*
 *  ChunkImpl.scala
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

import akka.stream.stage.GraphStageLogic
import akka.stream.{Inlet, Outlet, Shape}

import scala.annotation.tailrec

/** An I/O process that processes chunks with equal number of
  * input and output frames.
  */
trait ChunkImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, S <: Shape] {
  _: InOutImpl[S] with GraphStageLogic =>

  // ---- abstract ----

  protected def shouldComplete(): Boolean

  protected var bufIn0 : In0
  protected var bufOut0: Out

  protected def allocOutBuf0(): Out

  protected def in0 : Inlet [In0]
  protected def out0: Outlet[Out]

  protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit

  // ---- impl ----

  private[this] var inOff             = 0  // regarding `bufIn`
  private[this] var _inRemain         = 0
  private[this] var outOff            = 0  // regarding `bufOut`
  private[this] var outRemain         = 0
  private[this] var outSent           = true

  protected final def inRemain: Int = _inRemain

  @inline
  private[this] def shouldRead = _inRemain == 0 && canRead

  @tailrec
  final def process(): Unit = {
    logStream(s"process() $this")
    var stateChange = false

    if (shouldRead) {
      _inRemain    = readIns()
      inOff       = 0
      stateChange = true
    }

    if (outSent) {
      // XXX TODO -- use allocOutputBuffers
      bufOut0       = allocOutBuf0()
      outRemain     = bufOut0.size
      outOff        = 0
      outSent       = false
      stateChange   = true
    }

    val chunk = math.min(_inRemain, outRemain)
    if (chunk > 0) {
      processChunk(inOff = inOff, outOff = outOff, len = chunk)
      inOff       += chunk
      _inRemain   -= chunk
      outOff      += chunk
      outRemain   -= chunk
      stateChange  = true
    }

    val flushOut = shouldComplete()
    if (!outSent && (outRemain == 0 || flushOut) && isAvailable(out0)) {
      if (outOff > 0) {
        bufOut0.size = outOff
        push(out0, bufOut0)
      } else {
        bufOut0.release()
      }
      bufOut0     = null
      outSent     = true
      stateChange = true
    }

    if (flushOut && outSent) {
      logStream(s"completeStage() $this")
      completeStage()
    }
    else if (stateChange) process()
  }
}

trait FilterChunkImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, S <: Shape]
  extends ChunkImpl[In0, Out, S] {
  _: InOutImpl[S] with GraphStageLogic =>

  protected final def shouldComplete(): Boolean = inRemain == 0 && isClosed(in0)
}

trait GenChunkImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, S <: Shape]
  extends ChunkImpl[In0, Out, S] {
  _: InOutImpl[S] with GraphStageLogic =>

  protected final def shouldComplete(): Boolean = false
}