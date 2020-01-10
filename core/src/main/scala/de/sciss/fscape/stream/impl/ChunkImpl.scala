/*
 *  ChunkImpl.scala
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
package impl

import akka.stream.stage.GraphStageLogic
import akka.stream.{Inlet, Shape}

import scala.annotation.tailrec

/** An I/O process that processes chunks. */
trait ChunkImpl[S <: Shape] extends FullInOutImpl[S] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def shouldComplete(): Boolean

  protected def allocOutputBuffers(): Int

  /** Should read and possibly update `inRemain`, `outRemain`, `inOff`, `outOff`.
    *
    * @return `true` if this method did any actual processing.
    */
  protected def processChunk(): Boolean

  // ---- impl ----

  protected final var inOff           = 0  // regarding `bufIn`
  protected final var inRemain        = 0
  protected final var outOff          = 0  // regarding `bufOut`
  protected final var outRemain       = 0

  private[this] final var outSent     = true

  @inline
  private[this] def shouldRead = inRemain == 0 && canRead

  @tailrec
  final def process(): Unit = {
    logStream(s"process() $this")
    var stateChange = false

    if (shouldRead) {
      inRemain    = readIns()
      inOff       = 0
      stateChange = true
    }

    if (outSent) {
      outRemain     = allocOutputBuffers()
      outOff        = 0
      outSent       = false
      stateChange   = true
    }

    if (inValid && processChunk()) stateChange = true

    val flushOut = shouldComplete()
    if (!outSent && (outRemain == 0 || flushOut) && canWrite) {
      // logStream(s"writeOuts($outOff) $this - flushOut $flushOut")
      writeOuts(outOff)
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

/** An I/O process that processes chunks with equal number of
  * input and output frames.
  */
trait SameChunkImpl[S <: Shape] extends ChunkImpl[S] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def processChunk(inOff: Int, outOff: Int, len: Int): Unit

  // ---- impl ----

  protected final def processChunk(): Boolean = {
    val chunk = math.min(inRemain, outRemain)
    val res   = chunk > 0
    if (res) {
      processChunk(inOff = inOff, outOff = outOff, len = chunk)
      inOff       += chunk
      inRemain    -= chunk
      outOff      += chunk
      outRemain   -= chunk
    }
    res
  }
}

trait FilterChunkImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, S <: Shape] extends SameChunkImpl[S] {
  _: GraphStageLogic =>

  protected def in0: Inlet[In0]

  protected final def shouldComplete(): Boolean =
    inRemain == 0 && isClosed(in0) && !isAvailable(in0)
}

// XXX TODO --- remove unused type parameters
trait GenChunkImpl[In0 >: Null <: BufLike, Out >: Null <: BufLike, S <: Shape] extends SameChunkImpl[S] {
  _: GraphStageLogic =>

  protected final def shouldComplete(): Boolean = false
}