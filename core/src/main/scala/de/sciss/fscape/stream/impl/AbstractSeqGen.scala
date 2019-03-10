/*
 *  AbstractSeqGen.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
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

import akka.stream.FanInShape3

import scala.annotation.tailrec

// XXX TODO --- we could support dynamic `step` updates
abstract class AbstractSeqGen[A, E >: Null <: BufElem[A]](name: String, layer: Layer, shape: FanInShape3[E, E, BufL, E])
                                                     (implicit ctrl: Control, tpe: StreamType[A, E])
  extends NodeImpl(name, layer, shape)
    with GenIn3Impl[E, E, BufL, E] {

  // ---- abstract ----

  protected def inc(a: A, b: A): A

  // ---- impl ----

  private[this] var init = true

  private[this] var x     : A  = _
  private[this] var step  : A  = _
  private[this] var len   : Long    = -1L
  private[this] var frames: Long    = 0L

  protected final def allocOutBuf0(): E = tpe.allocBuf()

  @tailrec
  final def process(): Unit = {
    // println("process()")
    if (canRead) {
      // println("readIns()")
      readIns()
      if (init) {
        //  println("init")
        x       = bufIn0.buf(0)
        step    = bufIn1.buf(0)
        len     = math.max(1L, bufIn2.buf(0))
        init    = false
      }
    }

    if (canWrite && inValid) {
      // println("canWrite")
      val sz0     = allocOutputBuffers()
      val out     = bufOut0.buf
      val _len    = len
      val _frames = frames
      var _x      = x
      val _step   = step
      val chunk   = math.min(sz0, _len - _frames).toInt
      var i = 0
      while (i < chunk) {
        out(i) = _x
        _x     = inc(_x, _step)
        i += 1
      }
      val stop    = _frames + chunk
      frames      = stop
      x           = _x
      // println(s"chunk = $chunk, stop = $stop, len = ${_len}")

      if (stop == _len) {
        writeOuts(chunk)
        completeStage()
      } else {
        writeOuts(chunk)
        process()
      }
    }
  }
}