/*
 *  Impulse.scala
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

import akka.stream.{Attributes, FanInShape2}
import de.sciss.fscape.stream.impl.{GenChunkImpl, GenIn2IImpl, StageImpl, NodeImpl}
import de.sciss.numbers

object Impulse {
  def apply(freqN: OutD, phase: OutD)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(freqN, stage.in0)
    b.connect(phase, stage.in1)
    stage.out
  }

  private final val name = "Impulse"

  private type Shp = FanInShape2[BufD, BufD, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shp](name) {
    val shape: Shape = new FanInShape2(
      in0 = InD (s"$name.freqN"),
      in1 = InD (s"$name.phase"),
      out = OutI(s"$name.out"  )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(shape, layer)
  }

  // XXX TODO -- detect constant freq input and use multiplication instead of frame-by-frame addition for phase
  // (cf. Resample)
  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape)
      with GenChunkImpl[Shp]
      with GenIn2IImpl[BufD, BufD] {

    private[this] var incr    : Double = _
    private[this] var phaseOff: Double = _
    private[this] var phase   : Double = _    // internal state; does not include `phaseOff`
    private[this] var _init = true

    protected def processChunk(inOff: Int, outOff: Int, chunk: Int): Unit = {
      // println(s"Impulse.processChunk($bufIn0, $chunk)")

      var inOffI    = inOff
      var outOffI   = outOff
      val stop      = inOffI + chunk
      val b0        = if (bufIn0 == null) null else bufIn0.buf
      val b1        = if (bufIn1 == null) null else bufIn1.buf
      val out       = bufOut0.buf
      val stop0     = if (b0 == null) 0 else bufIn0.size
      val stop1     = if (b1 == null) 0 else bufIn1.size

      import numbers.Implicits._

      var incrV     = incr
      var phaseOffV = phaseOff
      var phaseV    = phase
      var y         = 0.0

      if (_init) {
        incrV     = b0(inOffI)
        phaseOffV = b1(inOffI).wrap(0.0, 1.0)
        phaseV    = -incrV
        y         = (phaseV + phaseOffV).wrap(0.0, 1.0)
        if (y == 0.0) y = 1.0
        _init     = false

      } else {
        y         = (phaseV + phaseOffV).wrap(0.0, 1.0)
      }

      while (inOffI < stop) {
        if (inOffI < stop0) incrV     = b0(inOffI)
        if (inOffI < stop1) phaseOffV = b1(inOffI).wrap(0.0, 1.0)
        val phaseVNew = (phaseV    + incrV    )   .wrap(0.0, 1.0)
        val x         = (phaseVNew + phaseOffV)   .wrap(0.0, 1.0)
        val t         = x < y
        out(outOffI)  = if (t) 1 else 0
        phaseV        = phaseVNew
        y             = x
        inOffI       += 1
        outOffI      += 1
      }
      incr      = incrV
      phaseOff  = phaseOffV
      phase     = phaseV
    }
  }
}