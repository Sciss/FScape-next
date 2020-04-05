/*
 *  Line.scala
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

import akka.stream.{Attributes, FanInShape3}
import de.sciss.fscape.stream.impl.{GenIn3DImpl, StageImpl, NodeImpl}

import scala.annotation.tailrec

object Line {
  def apply(start: OutD, end: OutD, length: OutL)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(start , stage.in0)
    b.connect(end   , stage.in1)
    b.connect(length, stage.in2)
    stage.out
  }

  private final val name = "Line"

  private type Shp = FanInShape3[BufD, BufD, BufL, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape3(
      in0 = InD (s"$name.start" ),
      in1 = InD (s"$name.end"   ),
      in2 = InL (s"$name.length"),
      out = OutD(s"$name.out"   )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO --- we could allow `start` and `end` to change over time,
  // although probably that will not be needed ever
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with GenIn3DImpl[BufD, BufD, BufL] {

    private[this] var _init = true

    private[this] var start : Double  = _
    private[this] var end   : Double  = _
    private[this] var slope : Double  = _
    private[this] var len   : Long    = -1L
    private[this] var frames: Long    = 0L

    @tailrec
    def process(): Unit = {
//      println("process()")
      if (canRead) {
//        println("readIns()")
        readIns()
        if (_init) {
//          println("init")
          start   = bufIn0.buf(0)
          end     = bufIn1.buf(0)
          len     = math.max(1L, bufIn2.buf(0))
          slope   = (end - start) / (len - 1)
          _init   = false
        }
      }

      if (canWrite && inValid) {
//        println("canWrite")
        val sz0     = allocOutputBuffers()
        val out     = bufOut0.buf
        val _len    = len
        val _frames = frames
        val _slope  = slope
        val _start  = start
        val chunk   = math.min(sz0, _len - _frames).toInt
        var i = 0
        while (i < chunk) {
          out(i) = (_frames + i) * _slope + _start
          i += 1
        }
        val stop     = _frames + chunk
        frames       = stop
//        println(s"chunk = $chunk, stop = $stop, len = ${_len}")

        if (stop == _len) {
          // replace last frame to match exactly the end value
          // to avoid problems with floating point noise
          out(chunk - 1) = end
          writeOuts(chunk)
          completeStage()
        } else {
          writeOuts(chunk)
          process()
        }
      }
    }
  }
}