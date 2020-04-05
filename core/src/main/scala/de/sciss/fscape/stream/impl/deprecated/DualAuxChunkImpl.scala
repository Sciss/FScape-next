/*
 *  DualAuxChunkImpl.scala
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

package de.sciss.fscape.stream.impl.deprecated

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.logStream

import scala.annotation.tailrec

/** An I/O process that processes chunks, distinguishing
  * between main or full-rate inputs and two types of auxiliary or
  * demand-rate inputs (for example, polling one value per window).
  */
@deprecated("Should move to using Handlers", since = "2.35.1")
trait DualAuxChunkImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def mainCanRead: Boolean
  protected def aux1CanRead: Boolean
  protected def aux2CanRead: Boolean

  protected def shouldComplete(): Boolean

  protected def allocOutputBuffers(): Int

  protected def readMainIns(): Int
  protected def readAux1Ins(): Int
  protected def readAux2Ins(): Int

  /** Should read and possibly update `inRemain`, `outRemain`, `inOff`, `outOff`.
    *
    * @return `true` if this method did any actual processing.
    */
  protected def processChunk(): Boolean

  // ---- impl ----

  protected final var mainInOff       = 0  // regarding `bufIn`
  protected final var mainInRemain    = 0
  protected final var aux1InOff       = 0
  protected final var aux1InRemain    = 0
  protected final var aux2InOff       = 0
  protected final var aux2InRemain    = 0
  protected final var outOff          = 0  // regarding `bufOut`
  protected final var outRemain       = 0

  private[this] final var outSent     = true

  @inline
  private[this] def mainShouldRead = mainInRemain == 0 && mainCanRead

  @inline
  private[this] def aux1ShouldRead  = aux1InRemain  == 0 && aux1CanRead

  @inline
  private[this] def aux2ShouldRead  = aux2InRemain  == 0 && aux2CanRead

  @tailrec
  final def process(): Unit = {
    logStream(s"process() $this")
    var stateChange = false

    if (mainShouldRead) {
      mainInRemain  = readMainIns()
      mainInOff     = 0
      stateChange   = true
    }

    if (aux1ShouldRead) {
      aux1InRemain  = readAux1Ins()
      aux1InOff     = 0
      stateChange   = true
    }

    if (aux2ShouldRead) {
      aux2InRemain  = readAux2Ins()
      aux2InOff     = 0
      stateChange   = true
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