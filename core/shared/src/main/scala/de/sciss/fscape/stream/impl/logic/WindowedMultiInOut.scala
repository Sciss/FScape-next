/*
 *  WindowedInAOutA.scala
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

package de.sciss.fscape.stream.impl.logic

import akka.stream.{Inlet, Outlet}
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.Node
import de.sciss.fscape.stream.impl.Handlers

import scala.annotation.tailrec
import scala.math.min

/** This is a building block for window processing UGens where there are
  * multiple hot inlets or outlets. It can also be used when there is only
  * one hot inlet, but other inlets are needed for filling the window buffer.
  */
trait WindowedMultiInOut extends Node {
  _: Handlers[_] =>

  // ---- abstract ----

  /** Tries to prepare the parameters for the next window.
    * If successful, returns `true` otherwise `false`. If successful,
    * it must be possible to successively call `readWinSize`. Most likely,
    * the implementation will allocate an internal window buffer here.
    */
  protected def tryObtainWinParams(): Boolean

  /** Called after a window has been fully read in. */
  protected def processWindow(): Unit

  /** Reads in a number of frames. */
  protected def readIntoWindow(n: Int): Unit

  /** Writes out a number of frames. */
  protected def writeFromWindow(n: Int): Unit

  /** The  number of frames to read in per window. */
  protected def readWinSize: Long

  /** The number of frames to write out per window. This is polled once after `processWindow`.
    */
  protected def writeWinSize: Long

  /** The number of frames available for input window processing. This is used
    * before calling `readIntoWindow`.
    */
  protected def mainInAvailable: Int

  /** The number of frames available for output window processing. This is used
    * before calling `writeFromWindow`.
    */
  protected def outAvailable: Int

  /** Whether crucial inputs are done and thus the output should be flushed and
    * the stage terminated.
    */
  protected def mainInDone: Boolean

  /** Whether all outputs are done and thus the stage should be terminated.
    */
  protected def outDone: Boolean

  /** Whether an inlet constitutes a crucial input whose closure should
    * result in stage termination.
    */
  protected def isHotIn(inlet: Inlet[_]): Boolean

  /** Flush all outlets. Returns `true` if ''all'' of them have been successfully flushed.
    */
  protected def flushOut(): Boolean

  // ---- default implementations that can be overridden if `super` is called ----

  protected def onDone(inlet: Inlet[_]): Unit =
    if (isHotIn(inlet)) {
      if (stage == 0 || (stage == 1 && readOff == 0L)) {
        stage = 2
        if (flushOut()) completeStage()
      } else if (stage == 1) { // i.e. readOff > 0
        enterStage2()
        process()
      }
    }

  protected def onDone(outlet: Outlet[_]): Unit =
    if (outDone) completeStage()

  private def enterStage2(): Unit = {
    processWindow()
    writeOff    = 0L
    writeRem    = writeWinSize
    stage       = 2
  }

  // ---- visible impl ----

  protected final var readRem   = 0L
  protected final var readOff   = 0L
  protected final var writeOff  = 0L
  protected final var writeRem  = 0L

  // ---- impl ----

  private[this] var stage = 0 // 0: gather window parameters, 1: gather input, 2: produce output

  @tailrec
  final protected def process(): Unit = {
    logStream(s"process() $this")

    if (stage == 0) {
      if (!tryObtainWinParams()) return
      readOff  = 0L
      readRem  = readWinSize
      stage     = 1
    }

    while (stage == 1) {
      val remIn = mainInAvailable
      if (remIn == 0) return
      val numIn = min(remIn, readRem).toInt
      if (numIn > 0) readIntoWindow(numIn)
      readOff += numIn
      readRem -= numIn
      if (mainInDone || readRem == 0) {
        enterStage2()
      }
    }

    while (stage == 2) {
      val remOut = outAvailable
      if (remOut == 0) return
      val numOut = min(remOut, writeRem).toInt
      if (numOut > 0) writeFromWindow(numOut)
      writeOff += numOut
      writeRem -= numOut
      if (writeRem == 0) {
        if (mainInDone) {
          if (flushOut()) completeStage()
          return
        } else {
          stage = 0
        }
      }
    }

    process()
  }
}
