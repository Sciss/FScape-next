/*
 *  DemandChunkImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.Shape
import akka.stream.stage.GraphStageLogic

import scala.annotation.tailrec

/** An I/O process that processes chunks, distinguishing
  * between main or full-rate inputs and auxiliary or
  * demand-rate inputs (for example, polling one value per window).
  */
trait DemandChunkImpl[S <: Shape] extends InOutImpl[S] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def mainCanRead: Boolean
  protected def auxCanRead : Boolean

  protected def shouldComplete(): Boolean

  protected def allocOutputBuffers(): Int

  protected def readMainIns(): Int
  protected def readAuxIns (): Int

  /** Should read and possibly update `inRemain`, `outRemain`, `inOff`, `outOff`.
    *
    * @return `true` if this method did any actual processing.
    */
  protected def processChunk(): Boolean

  // ---- impl ----

  protected final var mainInOff       = 0  // regarding `bufIn`
  protected final var mainInRemain    = 0
  protected final var auxInOff        = 0  // regarding `bufIn`
  protected final var auxInRemain     = 0
  protected final var outOff          = 0  // regarding `bufOut`
  protected final var outRemain       = 0

  private[this] final var outSent     = true

  @inline
  private[this] def mainShouldRead = mainInRemain == 0 && mainCanRead

  @inline
  private[this] def auxShouldRead  = auxInRemain  == 0 && auxCanRead

//  private[this] final var mainInValid = false
//  private[this] final var auxInValid  = false
//  private[this] final var _inValid    = false
//
//  protected final def inValid: Boolean = _inValid

  @tailrec
  final def process(): Unit = {
    logStream(s"process() $this")
    var stateChange = false

    if (mainShouldRead) {
      mainInRemain  = readMainIns()
      mainInOff     = 0
      stateChange   = true
//      if (!mainInValid) {
//        mainInValid = true
//        _inValid     = auxInValid
//      }
    }

    if (auxShouldRead) {
      auxInRemain   = readAuxIns()
      auxInOff      = 0
      stateChange   = true
//      if (!auxInValid) {
//        auxInValid  = true
//        _inValid     = mainInValid
//      }
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